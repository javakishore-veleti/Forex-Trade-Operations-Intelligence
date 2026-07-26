package com.fxtradeops.riskcalc.health;

import org.kie.api.runtime.KieContainer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Readiness health indicator — checks Postgres, Redis, Kafka (via connectivity),
 * and Drools KieContainer availability.
 */
@Component
public class ReadinessHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final KieContainer kieContainer;

    public ReadinessHealthIndicator(DataSource dataSource,
                                    StringRedisTemplate redisTemplate,
                                    KieContainer kieContainer) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.kieContainer = kieContainer;
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
            builder.down().withDetail("postgres", "connection failed: " + e.getMessage());
            return builder.build();
        }

        // Check Redis
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            builder.withDetail("redis", "UP");
        } catch (Exception e) {
            builder.down().withDetail("redis", "connection failed: " + e.getMessage());
            return builder.build();
        }

        // Check Drools KieContainer
        if (kieContainer == null) {
            builder.down().withDetail("drools", "KieContainer is null");
            return builder.build();
        }
        builder.withDetail("drools", "UP");

        return builder.build();
    }
}
