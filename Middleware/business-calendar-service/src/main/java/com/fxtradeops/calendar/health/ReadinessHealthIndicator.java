package com.fxtradeops.calendar.health;

import com.fxtradeops.calendar.domain.CalendarRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * ReadinessHealthIndicator reports UP only when PostgreSQL is reachable
 * AND the CalendarRegistry has loaded all region calendars.
 */
@Component("calendarReadiness")
public class ReadinessHealthIndicator implements HealthIndicator {

    private final CalendarRegistry calendarRegistry;
    private final DataSource dataSource;

    public ReadinessHealthIndicator(CalendarRegistry calendarRegistry, DataSource dataSource) {
        this.calendarRegistry = calendarRegistry;
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        // Check database connectivity
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                return Health.down().withDetail("reason", "Database connection not valid").build();
            }
        } catch (Exception e) {
            return Health.down().withDetail("reason", "Database unreachable: " + e.getMessage()).build();
        }

        // Check calendar registry loaded
        if (!calendarRegistry.isLoaded()) {
            return Health.down().withDetail("reason", "Calendar registry not yet loaded").build();
        }

        return Health.up()
                .withDetail("registryLoaded", true)
                .withDetail("regions", calendarRegistry.allCalendars().size())
                .build();
    }
}
