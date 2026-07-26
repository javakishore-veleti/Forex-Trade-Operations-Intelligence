package com.fxtradeops.eod.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration — business metrics via Micrometer (GP-Rq-8).
 * Metrics: eod_region_readiness{region,status}, eod_consolidation_total{outcome}, eod_blockers_open{region}
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> eodMeterRegistryCustomizer() {
        return registry -> registry.config().commonTags("service", "eod-processing-service");
    }
}
