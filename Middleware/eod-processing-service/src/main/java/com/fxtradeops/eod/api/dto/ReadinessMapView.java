package com.fxtradeops.eod.api.dto;

import java.util.Map;

/**
 * View of the readiness status map across all regions + GLOBAL.
 */
public record ReadinessMapView(Map<String, String> regions, String globalStatus) {
}
