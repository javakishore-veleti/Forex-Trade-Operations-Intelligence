package com.fxtradeops.reconciliation.domain.model;

/**
 * A violated cross-source invariant with a stable code and description.
 */
public record ViolatedInvariant(String code, String description) {
}
