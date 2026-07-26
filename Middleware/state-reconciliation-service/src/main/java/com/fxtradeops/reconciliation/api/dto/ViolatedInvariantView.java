package com.fxtradeops.reconciliation.api.dto;

/**
 * JSON view of a violated invariant.
 */
public record ViolatedInvariantView(String code, String description) {
}
