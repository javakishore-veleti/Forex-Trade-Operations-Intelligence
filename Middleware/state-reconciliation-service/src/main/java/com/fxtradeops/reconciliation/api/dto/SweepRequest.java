package com.fxtradeops.reconciliation.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for the batch sweep endpoint.
 */
public record SweepRequest(
        @NotEmpty(message = "tradeIds must not be empty")
        List<String> tradeIds
) {
}
