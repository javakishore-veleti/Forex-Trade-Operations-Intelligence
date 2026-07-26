package com.fxtradeops.tradeingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fxtradeops.domain.reference.RegionCode;
import com.fxtradeops.domain.trade.CurrencyPair;
import com.fxtradeops.domain.trade.TradeDirection;
import com.fxtradeops.tradeingest.api.dto.TradeRequest;
import com.fxtradeops.tradeingest.persistence.CapturedTradeEntity;
import com.fxtradeops.tradeingest.persistence.CapturedTradeRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test using Testcontainers (Postgres + Redis + Kafka).
 * All fixtures use FX- prefixed IDs and fictional counterparty/book names.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TradeCaptureIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("tradeingest")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withKraft();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.transaction-id-prefix", () -> "test-ingest-");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturedTradeRepository capturedTradeRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private TradeRequest validRequest() {
        return new TradeRequest(
                new CurrencyPair("USD", "INR", "USD/INR"),
                new BigDecimal("1500000.00"),
                "USD",
                TradeDirection.BUY,
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                "CP-FICTIONAL-001",
                "BOOK-FICTIONAL-001",
                RegionCode.APAC
        );
    }

    @Test
    void postValidTrade_returns201_persistsDbRow_publishesKafkaEvent() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .header("X-Correlation-Id", "corr-integ-001")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeId").exists())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andReturn();

        // Extract tradeId from response
        String responseBody = result.getResponse().getContentAsString();
        String tradeId = objectMapper.readTree(responseBody).get("tradeId").asText();
        assertThat(tradeId).matches("^FX-\\d{6}$");

        // Verify DB row exists at status CAPTURED
        Optional<CapturedTradeEntity> dbRow = capturedTradeRepository.findByTradeId(tradeId);
        assertThat(dbRow).isPresent();
        assertThat(dbRow.get().getStatus()).isEqualTo("CAPTURED");
        assertThat(dbRow.get().getCounterpartyId()).isEqualTo("CP-FICTIONAL-001");

        // Verify Kafka event was published
        List<ConsumerRecord<String, String>> records = consumeKafkaRecords("fxops.trade.events", tradeId);
        assertThat(records).isNotEmpty();
        String eventJson = records.get(0).value();
        assertThat(eventJson).contains(tradeId);
        assertThat(eventJson).contains("TRADE_CAPTURED");
    }

    @Test
    void replayIdempotencyKey_returns200_noSecondDbRow_noSecondKafkaEvent() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestJson = objectMapper.writeValueAsString(validRequest());

        // First call — 201
        MvcResult first = mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .header("X-Correlation-Id", "corr-integ-002")
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String firstTradeId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("tradeId").asText();

        long countAfterFirst = capturedTradeRepository.count();

        // Second call — 200 (replay)
        MvcResult second = mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .header("X-Correlation-Id", "corr-integ-003")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        String secondTradeId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("tradeId").asText();

        // Same tradeId returned
        assertThat(secondTradeId).isEqualTo(firstTradeId);

        // No second DB row
        long countAfterSecond = capturedTradeRepository.count();
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    private List<ConsumerRecord<String, String>> consumeKafkaRecords(String topic, String expectedKey) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        List<ConsumerRecord<String, String>> results = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedKey.equals(record.key())) {
                    results.add(record);
                }
            }
        }
        return results;
    }
}
