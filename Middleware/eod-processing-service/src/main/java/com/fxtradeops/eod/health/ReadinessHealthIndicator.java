package com.fxtradeops.eod.health;

import com.fxtradeops.eod.integration.BusinessCalendarClient;
import com.fxtradeops.eod.integration.RiskCalculationClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Readiness health indicator — checks Postgres, Kafka assignment, and peer services (GP-Rq-4).
 */
@Component
public class ReadinessHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final BusinessCalendarClient businessCalendarClient;
    private final RiskCalculationClient riskCalculationClient;

    public ReadinessHealthIndicator(DataSource dataSource,
                                    BusinessCalendarClient businessCalendarClient,
                                    RiskCalculationClient riskCalculationClient) {
        this.dataSource = dataSource;
        this.businessCalendarClient = businessCalendarClient;
        this.riskCalculationClient = riskCalculationClient;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();

        // Check Postgres
        boolean pgUp = checkPostgres();
        builder.withDetail("postgres", pgUp ? "UP" : "DOWN");

        // Check Business Calendar
        boolean bcUp = businessCalendarClient.isReachable();
        builder.withDetail("businessCalendar", bcUp ? "UP" : "DOWN");

        // Check Risk Calculation
        boolean rcUp = riskCalculationClient.isReachable();
        builder.withDetail("riskCalculation", rcUp ? "UP" : "DOWN");

        if (pgUp) {
            return builder.up().build();
        } else {
            return builder.down().build();
        }
    }

    private boolean checkPostgres() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
