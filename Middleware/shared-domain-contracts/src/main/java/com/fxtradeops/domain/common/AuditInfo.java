package com.fxtradeops.domain.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Audit metadata capturing creation and modification context.
 */
public record AuditInfo(
        @NotNull Instant createdAt,
        @NotBlank String createdBy,
        @NotNull Instant updatedAt,
        @NotBlank String updatedBy,
        Long version
) {
}
