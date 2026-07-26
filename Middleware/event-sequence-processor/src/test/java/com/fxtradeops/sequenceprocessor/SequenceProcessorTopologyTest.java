package com.fxtradeops.sequenceprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test using TopologyTestDriver to verify the end-to-end
 * Kafka Streams topology for event sequence processing.
 */
class SequenceProcessorTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String INPUT_TOPIC = "fxops.trade.events";
    private static final String OUTPUT_TOPIC = "fxops.sequence.anomalies";
    private static final String STATE_STORE = "sequence-facts-store";

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        SequenceProcessorConfig config = new SequenceProcessorConfig(
                "dummy:1234",
                "test-app",
                INPUT_TOPIC,
                OUTPUT_TOPIC,
                STATE_STORE,
                Duration.ofSeconds(60)
        );

        SequenceProcessorTopology topologyBuilder = new SequenceProcessorTopology(config);
        Topology topology = topologyBuilder.buildTopology();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());

        testDriver = new TopologyTestDriver(topology, props);
        inputTopic = testDriver.createInputTopic(INPUT_TOPIC, new StringSerializer(), new StringSerializer());
        outputTopic = testDriver.createOutputTopic(OUTPUT_TOPIC, new StringDeserializer(), new StringDeserializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void shouldNotProduceAnomaly_forNormalSequence() throws Exception {
        String tradeId = "FX-000001";

        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_CAPTURED"));
        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_VALIDATED"));
        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_ENRICHED"));

        assertTrue(outputTopic.isEmpty(), "No anomalies should be emitted for normal sequence");
    }

    @Test
    void shouldDetectMissingEvent_whenPredecessorSkipped() throws Exception {
        String tradeId = "FX-000002";

        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_CAPTURED"));
        // Skip TRADE_VALIDATED, jump to TRADE_ENRICHED
        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_ENRICHED"));

        assertFalse(outputTopic.isEmpty(), "Should emit MISSING_EVENT anomaly");
        String anomalyJson = outputTopic.readValue();
        assertTrue(anomalyJson.contains("MISSING_EVENT"));
        assertTrue(anomalyJson.contains("TRADE_VALIDATED"));
    }

    @Test
    void shouldDetectDuplicate_whenSameEventIdSentTwice() throws Exception {
        String tradeId = "FX-000003";
        String eventId = "fixed-event-id-123";

        String event = createEventWithId(tradeId, "TRADE_CAPTURED", eventId);
        inputTopic.pipeInput(tradeId, event);
        inputTopic.pipeInput(tradeId, event); // Exact same event

        assertFalse(outputTopic.isEmpty(), "Should emit DUPLICATE_EVENT anomaly");
        String anomalyJson = outputTopic.readValue();
        assertTrue(anomalyJson.contains("DUPLICATE_EVENT"));
    }

    @Test
    void shouldDetectOutOfOrder_whenEarlierEventArrivesLate() throws Exception {
        String tradeId = "FX-000004";

        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_CAPTURED"));
        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_VALIDATED"));
        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_ENRICHED"));
        // Now send TRADE_CAPTURED again — out of order
        inputTopic.pipeInput(tradeId, createEvent(tradeId, "TRADE_CAPTURED"));

        // Should have at least one OUT_OF_ORDER_EVENT anomaly
        boolean foundOutOfOrder = false;
        while (!outputTopic.isEmpty()) {
            String val = outputTopic.readValue();
            if (val.contains("OUT_OF_ORDER_EVENT")) {
                foundOutOfOrder = true;
            }
        }
        assertTrue(foundOutOfOrder, "Should emit OUT_OF_ORDER_EVENT anomaly");
    }

    private String createEvent(String tradeId, String eventType) throws Exception {
        return createEventWithId(tradeId, eventType, UUID.randomUUID().toString());
    }

    private String createEventWithId(String tradeId, String eventType, String eventId) throws Exception {
        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "tradeId", tradeId,
                "eventType", eventType,
                "correlationId", UUID.randomUUID().toString(),
                "sourceService", "test-service",
                "occurredAt", Instant.now().toString(),
                "schemaVersion", 1
        );
        return MAPPER.writeValueAsString(event);
    }
}
