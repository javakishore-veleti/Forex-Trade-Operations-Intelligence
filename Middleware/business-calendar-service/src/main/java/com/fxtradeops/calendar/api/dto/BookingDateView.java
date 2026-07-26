package com.fxtradeops.calendar.api.dto;

import com.fxtradeops.domain.reference.RegionCode;

import java.time.Instant;
import java.time.LocalDate;

public record BookingDateView(RegionCode region, Instant instant, LocalDate bookingDate) {
}
