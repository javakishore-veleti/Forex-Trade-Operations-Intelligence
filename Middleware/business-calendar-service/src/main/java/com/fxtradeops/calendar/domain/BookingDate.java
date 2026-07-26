package com.fxtradeops.calendar.domain;

import com.fxtradeops.domain.reference.RegionCode;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The business date to which a trade or event is assigned,
 * derived from its instant, the region's time zone, and the region's cutoff.
 */
public record BookingDate(RegionCode region, Instant instant, LocalDate bookingDate) {
}
