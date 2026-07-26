package com.fxtradeops.tradeingest.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Readiness health indicator that checks Postgres, Redis PING, and Kafka AdminClient.listTopics().
 */
@Component
public class ReadinessHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ReadinessHealthIndicator.class);

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final String kafkaBootstrapServers;

    public ReadinessHealthIndicator(
            DataSource dataSource,
            StringRedisTemplate redisTemplate,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String kafkaBootstrapServers) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // Check Postgres
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                builder.down().withDetail("postgres", "connection invalid");
                return builder.build();
            }
            builder.withDetail("postgres", "UP");
        } catch (Exception e) {
            log.warn("Postgres health check failed", e);
            builder.down().withDetail("postgres", "DOWN: " + e.getMessage());
            return builder.build();
        }

        // Check Redis
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            if (pong != null) {
                builder.withDetail("redis", "UP");
            } else {
                builder.down().withDetail("redis", "DOWN: no PONG");
                return builder.build();
            }
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            builder.down().withDetail("redis", "DOWN: " + e.getMessage());
            return builder.build();
        }

        // Check Kafka
        try (AdminClient adminClient = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers))) {
            adminClient.listTopics().names().get(5, TimeUnit.SECONDS);
            builder.withDetail("kafka", "UP");
        } catch (Exception e) {
            log.warn("Kafka health check failed", e);
            builder.down().withDetail("kafka", "DOWN: " + e.getMessage());
            return builder.build();
        }

        return builder.build();
    }
}
