package com.fxtradeops.calendar.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The 24-hour global processing day anchored to the base-country time zone (America/New_York).
 */
public record GlobalBusinessDate(Instant instant, LocalDate globalBusinessDate, ZoneId anchorZone) {
}
