package com.fxtradeops.calendar.domain;

import java.time.LocalDate;

/**
 * A configured non-business calendar date scoped to a specific region.
 */
public record Holiday(LocalDate date, String name) {
}
