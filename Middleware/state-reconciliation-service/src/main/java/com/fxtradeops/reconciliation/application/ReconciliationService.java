package com.fxtradeops.reconciliation.application;

import com.fxtradeops.domain.event.TradeEvent;
import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.action.PermittedAction;
import com.fxtradeops.reconciliation.domain.action.PermittedActionPolicy;
import com.fxtradeops.reconciliation.domain.canonical.CanonicalStateDeriver;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.divergence.DivergenceDetector;
import com.fxtradeops.reconciliation.domain.divergence.StaleSourceResolver;
import com.fxtradeops.reconciliation.domain.impact.BusinessImpact;
import com.fxtradeops.reconciliation.domain.impact.BusinessImpactClassifier;
import com.fxtradeops.reconciliation.domain.invariant.InvariantEvaluator;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.ObservedState;
import com.fxtradeops.reconciliation.domain.model.ReconciliationResult;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import com.fxtradeops.reconciliation.domain.model.ViolatedInvariant;
import com.fxtradeops.reconciliation.source.ObservedStateSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates the full reconciliation flow for a single trade:
 * 1. Read observed state from all sources
 * 2. Read ordered event history
 * 3. Derive canonical expected state
 * 4. Detect and classify divergences
 * 5. Evaluate invariants
 * 6. Compute permitted actions
 * 7. Classify business impact
 * 8. Assemble ReconciliationResult
 */
@Service
public class ReconciliationService {

    private final List<ObservedStateSource> sources;
    private final EventHistoryReader eventHistoryReader;
    private final CanonicalStateDeriver canonicalStateDeriver;
    private final DivergenceDetector divergenceDetector;
    private final StaleSourceResolver staleSourceResolver;
    private final InvariantEvaluator invariantEvaluator;
    private final PermittedActionPolicy permittedActionPolicy;
    private final BusinessImpactClassifier businessImpactClassifier;
    private final MeterRegistry meterRegistry;

    public ReconciliationService(
            List<ObservedStateSource> sources,
            EventHistoryReader eventHistoryReader,
            CanonicalStateDeriver canonicalStateDeriver,
            DivergenceDetector divergenceDetector,
            StaleSourceResolver staleSourceResolver,
            InvariantEvaluator invariantEvaluator,
            PermittedActionPolicy permittedActionPolicy,
            BusinessImpactClassifier businessImpactClassifier,
            MeterRegistry meterRegistry) {
        this.sources = sources;
        this.eventHistoryReader = eventHistoryReader;
        this.canonicalStateDeriver = canonicalStateDeriver;
        this.divergenceDetector = divergenceDetector;
        this.staleSourceResolver = staleSourceResolver;
        this.invariantEvaluator = invariantEvaluator;
        this.permittedActionPolicy = permittedActionPolicy;
        this.businessImpactClassifier = businessImpactClassifier;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Reconciles a single trade. Returns null if trade is unknown to all sources.
     */
    public ReconciliationResult reconcile(String tradeId) {
        // 1. Read observed state from all sources
        Map<SourceId, ObservedState> states = readAllSources(tradeId);

        // Check if trade is completely unknown (no source has data)
        boolean anyKnown = states.values().stream()
                .anyMatch(s -> s.available() && s.status() != null);
        if (!anyKnown) {
            // Also check if event history exists
            List<TradeEvent> history = eventHistoryReader.readOrderedHistory(tradeId);
            if (history.isEmpty()) {
                return null; // trade unknown to every source
            }
        }

        // 2. Read ordered event history for canonical derivation
        List<TradeEvent> orderedHistory = eventHistoryReader.readOrderedHistory(tradeId);

        // 3. Derive canonical expected state
        DerivationResult derivation = canonicalStateDeriver.derive(orderedHistory);
        TradeStatus canonical = derivation.state();

        // 4. Detect and classify divergences
        List<Divergence> divergences = divergenceDetector.detect(states, canonical);

        // 5. Identify most-likely-stale source
        SourceId mostLikelyStale = staleSourceResolver.resolve(divergences, states);

        // 6. Evaluate invariants
        List<ViolatedInvariant> violatedInvariants = invariantEvaluator.evaluate(
                states, canonical, derivation, divergences);

        // 7. Compute permitted actions
        Set<PermittedAction> permittedActions = permittedActionPolicy.permit(divergences, derivation);

        // 8. Classify business impact
        BusinessImpact impact = businessImpactClassifier.classify(derivation, divergences);

        // Record metrics
        recordMetrics(divergences, states, impact);

        return new ReconciliationResult(
                tradeId, states, canonical, derivation.status(),
                divergences, mostLikelyStale, violatedInvariants, permittedActions, impact
        );
    }

    private Map<SourceId, ObservedState> readAllSources(String tradeId) {
        Map<SourceId, ObservedState> states = new EnumMap<>(SourceId.class);
        for (ObservedStateSource source : sources) {
            ObservedState observed = source.read(tradeId);
            states.put(source.sourceId(), observed);
            if (!observed.available()) {
                Counter.builder("source_unavailable_total")
                        .tag("source", source.sourceId().name())
                        .register(meterRegistry)
                        .increment();
            }
        }
        return states;
    }

    private void recordMetrics(List<Divergence> divergences, Map<SourceId, ObservedState> states,
                               BusinessImpact impact) {
        String status = divergences.isEmpty() ? "CONSISTENT" : "DIVERGENT";
        Counter.builder("reconciliations_total")
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        for (Divergence d : divergences) {
            Counter.builder("divergences_total")
                    .tag("source", d.source().name())
                    .tag("classification", d.classification().name())
                    .register(meterRegistry)
                    .increment();
        }
    }
}
