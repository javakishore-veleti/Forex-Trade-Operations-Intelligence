package com.fxtradeops.eod.domain;

import java.util.Map;

/**
 * Projection of each region's close status plus the GLOBAL consolidation status.
 */
public record ReadinessStatusMap(Map<String, RegionalCloseStatus> regionStatuses, RegionalCloseStatus globalStatus) {
}
