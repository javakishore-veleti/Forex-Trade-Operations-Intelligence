package com.fxtradeops.reconciliation;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.api.dto.ReconciliationResultView;
import com.fxtradeops.reconciliation.domain.action.PermittedAction;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests with Testcontainers (Postgres + Mongo + Redis + Kafka).
 * All fixtures use synthetic FX- ids.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReconciliationIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fxtradeops")
            .withUsername("test")
            .withPassword("test");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("reconciliation.kafka.topic", () -> "trade-lifecycle-events");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setupSchema() {
        // Create table if not exists
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trade_current_state (
                    trade_id VARCHAR(50) PRIMARY KEY,
                    status VARCHAR(50) NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
    }

    /**
     * Req 8.1: All sources = canonical → CONSISTENT, zero divergences.
     */
    @Test
    @Order(1)
    @DisplayName("Consistent case: all sources agree with canonical → zero divergences")
    void consistentCase() {
        String tradeId = "FX-000001";
        Instant now = Instant.now();

        // Setup all sources to report BOOKED
        setupPostgres(tradeId, "BOOKED");
        setupMongoHistory(tradeId, List.of("TRADE_CAPTURED", "TRADE_VALIDATED", "TRADE_ENRICHED",
                "RISK_CALCULATION_COMPLETED", "TRADE_BOOKED"));
        setupMongoLatestAudit(tradeId, "BOOKED", now);
        setupRedis(tradeId, "BOOKED", now);

        ResponseEntity<ReconciliationResultView> response = restTemplate.getForEntity(
                "/api/v1/reconciliation/" + tradeId, ReconciliationResultView.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FX-000001", response.getBody().tradeId());
        assertEquals("BOOKED", response.getBody().expectedState());
        assertEquals("COMPLETE", response.getBody().derivation());
        assertTrue(response.getBody().divergences().isEmpty());
        assertEquals("NONE", response.getBody().businessImpact());
    }

    /**
     * Req 8.2: Canonical divergence scenario with multiple divergent sources.
     */
    @Test
    @Order(2)
    @DisplayName("Divergence scenario: multiple sources disagree")
    void divergenceScenario() {
        String tradeId = "FX-000002";
        Instant now = Instant.now();

        // History shows full path to CANCELLED
        setupPostgres(tradeId, "BOOKED");
        setupMongoHistory(tradeId, List.of("TRADE_CAPTURED", "TRADE_VALIDATED", "TRADE_ENRICHED",
                "RISK_CALCULATION_COMPLETED", "TRADE_BOOKED", "TRADE_CANCELLED"));
        setupMongoLatestAudit(tradeId, "RISK_CALCULATED", now.minusSeconds(60));
        setupRedis(tradeId, "CAPTURED", now.minusSeconds(120));

        ResponseEntity<ReconciliationResultView> response = restTemplate.getForEntity(
                "/api/v1/reconciliation/" + tradeId, ReconciliationResultView.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ReconciliationResultView result = response.getBody();
        assertNotNull(result);
        assertEquals("FX-000002", result.tradeId());
        assertEquals("CANCELLED", result.expectedState());
        assertEquals("COMPLETE", result.derivation());

        // Should have divergences
        assertFalse(result.divergences().isEmpty());

        // Every action must be in the catalogue
        Set<String> catalogue = Set.of("REFRESH_CACHE", "REPLAY_EVENT", "RESYNC_DOCUMENT_STORE",
                "RESYNC_RELATIONAL_STORE", "OPEN_RECONCILIATION_CASE", "NO_ACTION");
        for (String action : result.permittedActions()) {
            assertTrue(catalogue.contains(action), "Action " + action + " not in catalogue");
        }
    }

    /**
     * Req 8.3: Same trade reconciled twice → identical expectedState (determinism).
     */
    @Test
    @Order(3)
    @DisplayName("Determinism: same trade twice → identical expectedState")
    void determinismGuarantee() {
        String tradeId = "FX-000003";
        Instant now = Instant.now();

        setupPostgres(tradeId, "ENRICHED");
        setupMongoHistory(tradeId, List.of("TRADE_CAPTURED", "TRADE_VALIDATED", "TRADE_ENRICHED"));
        setupMongoLatestAudit(tradeId, "ENRICHED", now);
        setupRedis(tradeId, "ENRICHED", now);

        ResponseEntity<ReconciliationResultView> response1 = restTemplate.getForEntity(
                "/api/v1/reconciliation/" + tradeId, ReconciliationResultView.class);
        ResponseEntity<ReconciliationResultView> response2 = restTemplate.getForEntity(
                "/api/v1/reconciliation/" + tradeId, ReconciliationResultView.class);

        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        assertEquals(response1.getBody().expectedState(), response2.getBody().expectedState());
    }

    /**
     * Req 8.4: Unknown trade → 404.
     */
    @Test
    @Order(4)
    @DisplayName("Unknown trade → 404")
    void unknownTradeReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/reconciliation/FX-999999", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // --- Setup helpers ---

    private void setupPostgres(String tradeId, String status) {
        jdbcTemplate.update(
                "INSERT INTO trade_current_state (trade_id, status, updated_at) VALUES (?, ?, NOW()) " +
                        "ON CONFLICT (trade_id) DO UPDATE SET status = ?, updated_at = NOW()",
                tradeId, status, status);
    }

    private void setupMongoHistory(String tradeId, List<String> eventTypes) {
        // Clear existing history
        mongoTemplate.remove(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("tradeId").is(tradeId)),
                "trade_lifecycle_audit");

        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < eventTypes.size(); i++) {
            String eventType = eventTypes.get(i);
            String toStatus = mapEventTypeToStatus(eventType);
            Document doc = new Document()
                    .append("eventId", "evt-" + tradeId + "-" + i)
                    .append("tradeId", tradeId)
                    .append("correlationId", "corr-" + tradeId)
                    .append("eventType", eventType)
                    .append("toStatus", toStatus)
                    .append("occurredAt", Date.from(base.plusSeconds(i * 60L)))
                    .append("sequenceNumber", (long) (i + 1))
                    .append("sourceService", "test-setup");
            mongoTemplate.insert(doc, "trade_lifecycle_audit");
        }
    }

    private void setupMongoLatestAudit(String tradeId, String toStatus, Instant occurredAt) {
        // The history setup already inserts audit entries; this adds an explicit latest if needed
        // The DocumentStateSource reads the latest by occurredAt desc, so the history entries serve as both
    }

    private void setupRedis(String tradeId, String status, Instant timestamp) {
        redisTemplate.opsForValue().set("state:" + tradeId, status + "|" + timestamp.toString());
    }

    private String mapEventTypeToStatus(String eventType) {
        return switch (eventType) {
            case "TRADE_CAPTURED" -> "CAPTURED";
            case "TRADE_VALIDATED" -> "VALIDATED";
            case "TRADE_ENRICHED" -> "ENRICHED";
            case "RISK_CALCULATION_COMPLETED" -> "RISK_CALCULATED";
            case "TRADE_BOOKED" -> "BOOKED";
            case "TRADE_ALLOCATED" -> "ALLOCATED";
            case "TRADE_CONFIRMED" -> "CONFIRMED";
            case "TRADE_SETTLED" -> "SETTLED";
            case "TRADE_CANCELLED" -> "CANCELLED";
            case "TRADE_AMENDED" -> "AMENDED";
            case "TRADE_FAILED" -> "FAILED";
            default -> "CAPTURED";
        };
    }
}
