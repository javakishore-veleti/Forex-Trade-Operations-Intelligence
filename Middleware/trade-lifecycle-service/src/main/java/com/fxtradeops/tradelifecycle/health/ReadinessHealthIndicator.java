package com.fxtradeops.tradelifecycle.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Custom readiness health indicator checking Postgres, MongoDB, and Kafka consumer assignment.
 */
@Component
public class ReadinessHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final MongoTemplate mongoTemplate;
    private final KafkaListenerEndpointRegistry kafkaRegistry;

    public ReadinessHealthIndicator(DataSource dataSource,
                                     MongoTemplate mongoTemplate,
                                     KafkaListenerEndpointRegistry kafkaRegistry) {
        this.dataSource = dataSource;
        this.mongoTemplate = mongoTemplate;
        this.kafkaRegistry = kafkaRegistry;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // Check Postgres
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                return builder.down().withDetail("postgres", "Connection invalid").build();
            }
            builder.withDetail("postgres", "UP");
        } catch (Exception e) {
            return builder.down().withDetail("postgres", "Unavailable: " + e.getMessage()).build();
        }

        // Check MongoDB
        try {
            mongoTemplate.getDb().getName();
            builder.withDetail("mongodb", "UP");
        } catch (Exception e) {
            return builder.down().withDetail("mongodb", "Unavailable: " + e.getMessage()).build();
        }

        // Check Kafka consumer assignment
        try {
            boolean anyRunning = kafkaRegistry.getListenerContainers().stream()
                    .anyMatch(MessageListenerContainer::isRunning);
            if (anyRunning) {
                builder.withDetail("kafka", "UP - consumer assigned");
            } else {
                builder.withDetail("kafka", "No active consumers");
            }
        } catch (Exception e) {
            builder.withDetail("kafka", "Check failed: " + e.getMessage());
        }

        return builder.build();
    }
}
