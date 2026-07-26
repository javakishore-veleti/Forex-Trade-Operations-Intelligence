package com.fxtradeops.eod.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Response body for 409 Conflict when consolidation is attempted but regions are not ready.
 */
public record NotReadyConflict(
        int status,
        String error,
        String message,
        List<String> notReadyRegions,
        Map<String, List<String>> blockers,
        String timestamp
) {
}
