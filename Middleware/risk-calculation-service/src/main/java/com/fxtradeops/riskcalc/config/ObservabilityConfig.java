package com.fxtradeops.riskcalc.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration placeholder.
 * Metrics are registered inline via MeterRegistry in RiskEngine and other components.
 * Business metrics exposed:
 * - risk_calculations_total{region,risk_level}
 * - risk_calculation_duration_seconds
 * - fallback_rule_firings_total{pair}
 */
@Configuration
public class ObservabilityConfig {
    // Metrics auto-registered via Micrometer + Actuator + Prometheus registry
}
