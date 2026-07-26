package com.fxtradeops.calendar.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record GlobalBusinessDateView(Instant instant, LocalDate globalBusinessDate, ZoneId anchorZone) {
}
