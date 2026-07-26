package com.fxtradeops.reconciliation.source.analytics;

import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.source.ObservedStateSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Optional read-only adapter for Analytics Platform (Databricks).
 * Only active when reconciliation.sources.analytics.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "reconciliation.sources.analytics.enabled", havingValue = "true")
public class AnalyticsStateSource implements ObservedStateSource {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsStateSource.class);

    @Override
    public SourceId sourceId() {
        return SourceId.ANALYTICS_PLATFORM;
    }

    @Override
    public ObservedState read(String tradeId) {
        try {
            // TODO: Implement Databricks query when analytics platform is integrated
            log.debug("[{}] Analytics platform query for trade {} — not yet implemented",
                    MDC.get("correlationId"), tradeId);
            return ObservedState.unavailable(SourceId.ANALYTICS_PLATFORM);
        } catch (Exception e) {
            log.warn("[{}] Failed to read ANALYTICS_PLATFORM source for trade {}: {}",
                    MDC.get("correlationId"), tradeId, e.getMessage());
            return ObservedState.unavailable(SourceId.ANALYTICS_PLATFORM);
        }
    }
}
