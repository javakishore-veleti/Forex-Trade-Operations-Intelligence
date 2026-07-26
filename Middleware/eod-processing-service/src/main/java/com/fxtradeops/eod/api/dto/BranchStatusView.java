package com.fxtradeops.eod.api.dto;

import java.time.Instant;

/**
 * View of a branch's completion status.
 */
public record BranchStatusView(String branchId, String regionCode, boolean complete, Instant completedAt) {
}
