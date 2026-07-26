package com.fxtradeops.calendar.api.dto;

import com.fxtradeops.domain.reference.RegionCode;

import java.time.LocalDate;

public record BusinessDayView(RegionCode region, LocalDate date, boolean businessDay, String reason) {
}
