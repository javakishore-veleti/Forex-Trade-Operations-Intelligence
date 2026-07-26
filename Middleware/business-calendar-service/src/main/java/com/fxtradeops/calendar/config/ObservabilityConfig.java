package com.fxtradeops.calendar.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration — Micrometer business metrics.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public CalendarMetrics calendarMetrics(MeterRegistry registry) {
        return new CalendarMetrics(registry);
    }

    public static class CalendarMetrics {

        private final MeterRegistry registry;

        public CalendarMetrics(MeterRegistry registry) {
            this.registry = registry;
        }

        public void recordBookingDate(String region) {
            Counter.builder("calendar_booking_date_total")
                    .tag("region", region)
                    .register(registry)
                    .increment();
        }

        public void recordBusinessDay(String region, String reason) {
            Counter.builder("calendar_business_day_total")
                    .tag("region", region)
                    .tag("reason", reason)
                    .register(registry)
                    .increment();
        }
    }
}
