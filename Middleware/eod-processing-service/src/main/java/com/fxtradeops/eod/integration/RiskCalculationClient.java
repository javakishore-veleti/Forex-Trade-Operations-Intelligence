package com.fxtradeops.eod.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * REST client for the Risk Calculation peer service.
 * Checks existence of a region's EOD risk snapshot.
 */
@Component
public class RiskCalculationClient {

    private static final Logger log = LoggerFactory.getLogger(RiskCalculationClient.class);

    private final RestClient restClient;

    public RiskCalculationClient(
            @Value("${eod.peer.risk-calculation.base-url}") String baseUrl,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Check if a risk snapshot exists for the given region and business date.
     */
    public boolean snapshotExists(String region, LocalDate businessDate) {
        try {
            String response = restClient.get()
                    .uri("/api/v1/risk/snapshots/{region}/{businessDate}/exists", region, businessDate)
                    .retrieve()
                    .body(String.class);
            return "true".equalsIgnoreCase(response);
        } catch (Exception e) {
            log.warn("Failed to check risk snapshot for region={}, date={}: {}",
                    region, businessDate, e.getMessage());
            return false;
        }
    }

    /**
     * Check if the Risk Calculation service is reachable.
     */
    public boolean isReachable() {
        try {
            restClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
