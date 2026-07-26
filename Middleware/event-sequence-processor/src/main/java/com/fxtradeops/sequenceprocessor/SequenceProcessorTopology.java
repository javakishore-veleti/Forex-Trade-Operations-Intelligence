package com.fxtradeops.sequenceprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fxtradeops.domain.event.TradeEventType;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the Kafka Streams topology for event sequence processing.
 * <p>
 * Flow: consume fxops.trade.events → update SequenceFact in state store
 * → detect anomalies → publish to fxops.sequence.anomalies
 * <p>
 * Uses the low-level Processor API (Topology) to forward anomaly records
 * directly to the output sink.
 */
public class SequenceProcessorTopology {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    static final String SOURCE_NODE = "trade-events-source";
    static final String PROCESSOR_NODE = "sequence-fact-processor";
    static final String SINK_NODE = "anomaly-sink";

    private final SequenceProcessorConfig config;
    private final MissingEventDetector missingDetector;
    private final DuplicateEventDetector duplicateDetector;
    private final OutOfOrderEventDetector outOfOrderDetector;

    public SequenceProcessorTopology(SequenceProcessorConfig config) {
        this.config = config;
        this.missingDetector = new MissingEventDetector();
        this.duplicateDetector = new DuplicateEventDetector();
        this.outOfOrderDetector = new OutOfOrderEventDetector();
    }

    public Topology buildTopology() {
        Topology topology = new Topology();

        // State store for SequenceFact per tradeId
        StoreBuilder<KeyValueStore<String, String>> storeBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(config.stateStoreName()),
                Serdes.String(),
                Serdes.String()
        ).withLoggingEnabled(Map.of()); // changelog topic enables state recovery

        topology.addSource(SOURCE_NODE, Serdes.String().deserializer(), Serdes.String().deserializer(), config.inputTopic());

        topology.addProcessor(PROCESSOR_NODE,
                (ProcessorSupplier<String, String, String, String>) () ->
                        new SequenceFactProcessor(config, missingDetector, duplicateDetector, outOfOrderDetector),
                SOURCE_NODE);

        topology.addStateStore(storeBuilder, PROCESSOR_NODE);

        topology.addSink(SINK_NODE, config.outputTopic(), Serdes.String().serializer(), Serdes.String().serializer(), PROCESSOR_NODE);

        return topology;
    }

    /**
     * The core processor that maintains SequenceFact state and emits anomaly envelopes.
     */
    static class SequenceFactProcessor implements Processor<String, String, String, String> {

        private final SequenceProcessorConfig config;
        private final MissingEventDetector missingDetector;
        private final DuplicateEventDetector duplicateDetector;
        private final OutOfOrderEventDetector outOfOrderDetector;
        private ProcessorContext<String, String> context;
        private KeyValueStore<String, String> stateStore;

        SequenceFactProcessor(
                SequenceProcessorConfig config,
                MissingEventDetector missingDetector,
                DuplicateEventDetector duplicateDetector,
                OutOfOrderEventDetector outOfOrderDetector
        ) {
            this.config = config;
            this.missingDetector = missingDetector;
            this.duplicateDetector = duplicateDetector;
            this.outOfOrderDetector = outOfOrderDetector;
        }

        @Override
        public void init(ProcessorContext<String, String> context) {
            this.context = context;
            this.stateStore = context.getStateStore(config.stateStoreName());
        }

        @Override
        public void process(Record<String, String> record) {
            try {
                String tradeId = record.key();
                String value = record.value();
                Map<String, Object> eventMap = MAPPER.readValue(value, Map.class);

                String eventId = (String) eventMap.get("eventId");
                String eventTypeStr = (String) eventMap.get("eventType");
                String correlationId = (String) eventMap.get("correlationId");

                if (eventId == null || eventTypeStr == null || correlationId == null) {
                    return; // Malformed — skip
                }

                TradeEventType eventType;
                try {
                    eventType = TradeEventType.valueOf(eventTypeStr);
                } catch (IllegalArgumentException e) {
                    return; // Unknown event type — skip
                }

                // Load existing fact or create new
                SequenceFact fact = loadFact(tradeId);
                List<AnomalyEnvelope> anomalies = new ArrayList<>();

                // Compute payload hash for duplicate detection
                String payloadHash = String.valueOf(value.hashCode());

                // 1. Duplicate detection
                Optional<AnomalyEnvelope> dupAnomaly = duplicateDetector.detect(
                        fact, eventId, eventType, correlationId, payloadHash, payloadHash);
                dupAnomaly.ifPresent(anomalies::add);

                if (dupAnomaly.isPresent()) {
                    // Record duplicate but don't update status
                    List<String> dupIds = new ArrayList<>(fact.duplicateEventIds());
                    dupIds.add(eventId);
                    fact = new SequenceFact(
                            fact.tradeId(), fact.observedEvents(), fact.expectedNextEvents(),
                            fact.missingEvents(), dupIds, fact.sequenceViolations(),
                            fact.lastStatus(), Instant.now());
                    storeFact(tradeId, fact);
                    // Emit anomalies
                    for (AnomalyEnvelope a : anomalies) {
                        forwardAnomaly(record, a);
                    }
                    return;
                }

                // 2. Missing event detection
                Optional<AnomalyEnvelope> missingAnomaly = missingDetector.detect(fact, eventType, correlationId);
                missingAnomaly.ifPresent(anomalies::add);

                // 3. Missing event resolution
                Optional<AnomalyEnvelope> resolvedAnomaly = missingDetector.detectResolution(fact, eventType, correlationId);
                resolvedAnomaly.ifPresent(anomalies::add);

                // 4. Out-of-order detection
                Optional<AnomalyEnvelope> oooAnomaly = outOfOrderDetector.detect(fact, eventType, correlationId);
                oooAnomaly.ifPresent(anomalies::add);

                // Update the SequenceFact
                fact = updateFact(fact, tradeId, eventId, eventType);
                storeFact(tradeId, fact);

                // Emit all detected anomalies
                for (AnomalyEnvelope a : anomalies) {
                    forwardAnomaly(record, a);
                }

            } catch (Exception e) {
                // Processing error — log but don't crash the stream
                // In production, this would go to DLQ
            }
        }

        private SequenceFact loadFact(String tradeId) {
            String json = stateStore.get(tradeId);
            if (json == null) {
                return new SequenceFact(
                        tradeId, List.of(), List.of(), List.of(), List.of(), List.of(), null, Instant.now());
            }
            try {
                return MAPPER.readValue(json, SequenceFact.class);
            } catch (Exception e) {
                return new SequenceFact(
                        tradeId, List.of(), List.of(), List.of(), List.of(), List.of(), null, Instant.now());
            }
        }

        private void storeFact(String tradeId, SequenceFact fact) {
            try {
                stateStore.put(tradeId, MAPPER.writeValueAsString(fact));
            } catch (Exception e) {
                // Serialization error — should not happen with well-formed records
            }
        }

        private SequenceFact updateFact(SequenceFact fact, String tradeId, String eventId, TradeEventType eventType) {
            // Add to observed events
            List<SequenceFact.ObservedEvent> observed = new ArrayList<>(fact.observedEvents());
            observed.add(new SequenceFact.ObservedEvent(eventId, eventType, Instant.now()));

            // Update missing events
            List<TradeEventType> missing = new ArrayList<>(fact.missingEvents());
            missing.remove(eventType); // Resolve if it was missing

            // Add new missing if predecessor not observed
            TradeEventType requiredPred = TradeLifecycleStateMachine.requiredPredecessor(eventType);
            if (requiredPred != null) {
                boolean predObserved = observed.stream().anyMatch(o -> o.eventType() == requiredPred);
                if (!predObserved && !missing.contains(requiredPred)) {
                    missing.add(requiredPred);
                }
            }

            // Update status — only advance, don't go backwards for out-of-order
            TradeEventType newStatus = fact.lastStatus();
            int currentOrdinal = TradeLifecycleStateMachine.lifecycleOrdinal(fact.lastStatus());
            int arrivingOrdinal = TradeLifecycleStateMachine.lifecycleOrdinal(eventType);
            if (arrivingOrdinal > currentOrdinal || TradeLifecycleStateMachine.isTerminal(eventType)) {
                newStatus = eventType;
            }

            // Compute expected next
            Set<TradeEventType> nextSet = TradeLifecycleStateMachine.expectedNext(
                    newStatus != null ? newStatus : eventType);
            List<TradeEventType> expectedNext = new ArrayList<>(nextSet);

            // Update violations list
            List<String> violations = new ArrayList<>(fact.sequenceViolations());

            return new SequenceFact(
                    tradeId, observed, expectedNext, missing,
                    fact.duplicateEventIds(), violations, newStatus, Instant.now()
            );
        }

        private void forwardAnomaly(Record<String, String> sourceRecord, AnomalyEnvelope anomaly) {
            try {
                String json = MAPPER.writeValueAsString(anomaly);
                context.forward(new Record<>(anomaly.tradeId(), json, sourceRecord.timestamp()));
            } catch (Exception e) {
                // Serialization error
            }
        }
    }
}
