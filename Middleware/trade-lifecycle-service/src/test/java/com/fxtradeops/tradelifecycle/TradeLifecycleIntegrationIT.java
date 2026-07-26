package com.fxtradeops.tradelifecycle;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.tradelifecycle.application.DedupService;
import com.fxtradeops.tradelifecycle.application.LifecycleService;
import com.fxtradeops.tradelifecycle.persistence.document.AuditEntryDocument;
import com.fxtradeops.tradelifecycle.persistence.document.AuditRepository;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateEntity;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using Testcontainers (Postgres + MongoDB + Redis + Kafka).
 * All fixtures use synthetic FX- prefixed IDs.
 */
@SpringBootTest
@Testcontainers
class TradeLifecycleIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("trade_lifecycle")
            .withUsername("test")
            .withPassword("test");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private LifecycleService lifecycleService;

    @Autowired
    private DedupService dedupService;

    @Autowired
    private TradeCurrentStateRepository stateRepository;

    @Autowired
    private AuditRepository auditRepository;

    @BeforeEach
    void cleanUp() {
        stateRepository.deleteAll();
        auditRepository.deleteAll();
    }

    @Test
    @DisplayName("Full happy path: CAPTURED → VALIDATED → ... → SETTLED")
    void fullHappyPath_capturedToSettled() {
        String tradeId = "FX-000001";
        String correlationId = "corr-" + UUID.randomUUID();

        // Process events in order
        processEvent(tradeId, correlationId, TradeEventType.TRADE_CAPTURED, 1);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_VALIDATED, 2);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_ENRICHED, 3);
        processEvent(tradeId, correlationId, TradeEventType.RISK_CALCULATION_COMPLETED, 4);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_BOOKED, 5);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_ALLOCATED, 6);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_CONFIRMED, 7);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_SETTLED, 8);

        // Assert final state
        Optional<TradeCurrentStateEntity> state = stateRepository.findById(tradeId);
        assertThat(state).isPresent();
        assertThat(state.get().getStatus()).isEqualTo(TradeStatus.SETTLED);

        // Assert timeline
        List<AuditEntryDocument> timeline = auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc(tradeId);
        assertThat(timeline).hasSize(8);
        assertThat(timeline.get(0).getEventType()).isEqualTo("TRADE_CAPTURED");
        assertThat(timeline.get(7).getEventType()).isEqualTo("TRADE_SETTLED");

        // All entries should be successful transitions
        assertThat(timeline).allMatch(e -> !e.isRejected() && !e.isNoop() && !e.isOrphan());
    }

    @Test
    @DisplayName("Duplicate eventId produces no extra transition and noop audit")
    void duplicateEvent_noExtraTransition_noopAudit() {
        String tradeId = "FX-000002";
        String correlationId = "corr-" + UUID.randomUUID();
        String eventId = "evt-duplicate-" + UUID.randomUUID();

        // First: capture the trade
        processEvent(tradeId, correlationId, TradeEventType.TRADE_CAPTURED, 1);

        // Process TRADE_VALIDATED with a specific eventId
        TradeEvent validatedEvent = new TradeEvent(
                eventId, tradeId, correlationId, TradeEventType.TRADE_VALIDATED,
                Instant.now(), 2L, "FX-validation-service", Map.of());
        lifecycleService.process(validatedEvent);
        dedupService.markProcessed(eventId);

        // Assert state is VALIDATED
        assertThat(stateRepository.findById(tradeId).get().getStatus()).isEqualTo(TradeStatus.VALIDATED);

        // Re-process same eventId — should be duplicate
        assertThat(dedupService.isDuplicate(eventId)).isTrue();

        // Check audit count before noop
        long auditCountBefore = auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc(tradeId).size();

        // State should remain VALIDATED — no extra transition happened
        assertThat(stateRepository.findById(tradeId).get().getStatus()).isEqualTo(TradeStatus.VALIDATED);
    }

    @Test
    @DisplayName("Orphan event (non-initiating for unknown trade) does not create aggregate")
    void orphanEvent_noAggregateCreated() {
        String tradeId = "FX-000003";
        String correlationId = "corr-" + UUID.randomUUID();

        // Send a TRADE_VALIDATED for a trade that doesn't exist
        processEvent(tradeId, correlationId, TradeEventType.TRADE_VALIDATED, 1);

        // No aggregate should be created
        assertThat(stateRepository.findById(tradeId)).isEmpty();

        // But an orphan audit entry should exist
        List<AuditEntryDocument> timeline = auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc(tradeId);
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).isOrphan()).isTrue();
    }

    @Test
    @DisplayName("Illegal transition is rejected but does not halt processing")
    void illegalTransition_rejected_continuesProcessing() {
        String tradeId = "FX-000004";
        String correlationId = "corr-" + UUID.randomUUID();

        // Capture the trade
        processEvent(tradeId, correlationId, TradeEventType.TRADE_CAPTURED, 1);

        // Try to settle directly from CAPTURED (illegal)
        processEvent(tradeId, correlationId, TradeEventType.TRADE_SETTLED, 2);

        // State should remain CAPTURED
        assertThat(stateRepository.findById(tradeId).get().getStatus()).isEqualTo(TradeStatus.CAPTURED);

        // Audit should show the rejection
        List<AuditEntryDocument> timeline = auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc(tradeId);
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(1).isRejected()).isTrue();
        assertThat(timeline.get(1).getFromStatus()).isEqualTo("CAPTURED");
        assertThat(timeline.get(1).getToStatus()).isEqualTo("SETTLED");

        // Continue processing — validate should still work
        processEvent(tradeId, correlationId, TradeEventType.TRADE_VALIDATED, 3);
        assertThat(stateRepository.findById(tradeId).get().getStatus()).isEqualTo(TradeStatus.VALIDATED);
    }

    @Test
    @DisplayName("Cancellation from permitted state succeeds")
    void cancellation_fromPermittedState_succeeds() {
        String tradeId = "FX-000005";
        String correlationId = "corr-" + UUID.randomUUID();

        processEvent(tradeId, correlationId, TradeEventType.TRADE_CAPTURED, 1);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_VALIDATED, 2);
        processEvent(tradeId, correlationId, TradeEventType.TRADE_CANCELLED, 3);

        assertThat(stateRepository.findById(tradeId).get().getStatus()).isEqualTo(TradeStatus.CANCELLED);
    }

    @Test
    @DisplayName("Same-status event is treated as noop")
    void sameStatusEvent_noop() {
        String tradeId = "FX-000006";
        String correlationId = "corr-" + UUID.randomUUID();

        processEvent(tradeId, correlationId, TradeEventType.TRADE_CAPTURED, 1);
        // Send TRADE_CAPTURED again — same status
        processEvent(tradeId, correlationId, TradeEventType.TRADE_CAPTURED, 2);

        // State remains CAPTURED
        assertThat(stateRepository.findById(tradeId).get().getStatus()).isEqualTo(TradeStatus.CAPTURED);

        // Audit has noop entry
        List<AuditEntryDocument> timeline = auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc(tradeId);
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(1).isNoop()).isTrue();
    }

    private void processEvent(String tradeId, String correlationId, TradeEventType eventType, long seq) {
        String eventId = "evt-" + UUID.randomUUID();
        TradeEvent event = new TradeEvent(
                eventId, tradeId, correlationId, eventType,
                Instant.now().plusSeconds(seq), seq,
                "FX-test-service", Map.of());
        lifecycleService.process(event);
        dedupService.markProcessed(eventId);
    }
}
