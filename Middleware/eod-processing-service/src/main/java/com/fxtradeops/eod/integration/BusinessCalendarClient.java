package com.fxtradeops.eod.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * REST client for the Business Calendar peer service.
 * Provides current Global Business Date and booking-date classification.
 */
@Component
public class BusinessCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(BusinessCalendarClient.class);

    private final RestClient restClient;

    public BusinessCalendarClient(
            @Value("${eod.peer.business-calendar.base-url}") String baseUrl,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Obtain the current Global Business Date from the Business Calendar service.
     */
    public LocalDate currentGlobalBusinessDate() {
        try {
            String response = restClient.get()
                    .uri("/api/v1/calendar/business-date/current")
                    .retrieve()
                    .body(String.class);
            return response != null ? LocalDate.parse(response.replace("\"", "")) : LocalDate.now();
        } catch (Exception e) {
            log.warn("Failed to fetch global business date from Business Calendar, using current date: {}",
                    e.getMessage());
            return LocalDate.now();
        }
    }

    /**
     * Check if the Business Calendar service is reachable.
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
