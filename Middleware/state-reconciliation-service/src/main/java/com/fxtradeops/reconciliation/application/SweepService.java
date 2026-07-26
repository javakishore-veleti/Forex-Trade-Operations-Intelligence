package com.fxtradeops.reconciliation.application;

import com.fxtradeops.reconciliation.domain.model.ReconciliationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Batch/on-demand sweep over a set of tradeIds.
 * Returns one ReconciliationResult per trade (nulls for unknown trades are filtered out).
 */
@Service
public class SweepService {

    private final ReconciliationService reconciliationService;

    public SweepService(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Reconciles multiple trades and returns results for each known trade.
     */
    public List<ReconciliationResult> sweep(List<String> tradeIds) {
        return tradeIds.stream()
                .map(reconciliationService::reconcile)
                .filter(Objects::nonNull)
                .toList();
    }
}
