package com.fxtradeops.domain.reference;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A trading book to which trades are assigned.
 */
public record TradingBook(
        @NotBlank String bookId,
        @NotBlank String bookName,
        @NotNull RegionCode regionCode,
        @NotBlank String traderId,
        boolean isActive,
        @NotNull BookType bookType
) {
}
