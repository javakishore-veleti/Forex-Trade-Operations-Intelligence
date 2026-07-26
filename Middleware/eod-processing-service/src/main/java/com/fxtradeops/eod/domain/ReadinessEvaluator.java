package com.fxtradeops.eod.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure function evaluating regional readiness from collected inputs.
 * No I/O, no clock — trivially unit-testable and deterministic (GP-Rq-13).
 */
public final class ReadinessEvaluator {

    private ReadinessEvaluator() {
        // utility class
    }

    /**
     * Evaluate regional readiness.
     * READY only when all four inputs are satisfied; otherwise BLOCKED with specific unmet conditions.
     */
    public static ReadinessResult evaluate(ReadinessInputs in) {
        List<Blocker> unmet = new ArrayList<>();

        if (!in.allBranchesComplete()) {
            unmet.add(Blocker.of(BlockerType.INCOMPLETE_BRANCH, in.incompleteBranches()));
        }

        if (in.unprocessedTradeCount() > in.tolerance() && !in.toleranceExceptionApproved()) {
            unmet.add(Blocker.of(BlockerType.UNPROCESSED_TRADES, in.unprocessedTradeCount()));
        }

        if (!in.riskSnapshotExists()) {
            unmet.add(Blocker.of(BlockerType.MISSING_RISK_SNAPSHOT, in.region()));
        }

        unmet.addAll(in.openBlockers());

        return unmet.isEmpty()
                ? ReadinessResult.ready()
                : ReadinessResult.blocked(unmet);
    }
}
