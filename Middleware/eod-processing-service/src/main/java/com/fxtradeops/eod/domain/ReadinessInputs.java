package com.fxtradeops.eod.domain;

import java.util.List;

/**
 * Collected inputs for the pure readiness evaluation function.
 */
public record ReadinessInputs(
        String region,
        boolean allBranchesComplete,
        String incompleteBranches,
        int unprocessedTradeCount,
        int tolerance,
        boolean toleranceExceptionApproved,
        boolean riskSnapshotExists,
        List<Blocker> openBlockers
) {
}
