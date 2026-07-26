package com.fxtradeops.domain.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Pagination and sort parameters for list queries.
 */
public record PageRequest(
        @Min(0) int page,
        @Min(1) @Max(100) int size,
        String sortBy,
        String sortDirection
) {
}
