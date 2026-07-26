package com.fxtradeops.domain.common;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Generic paginated list response.
 */
public record PageResponse<T>(
        @NotNull List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
