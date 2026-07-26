package com.fxtradeops.calendar.api.dto;

import com.fxtradeops.domain.reference.RegionCode;

import java.time.LocalDate;

public record BusinessDaysBetweenView(RegionCode region, LocalDate from, LocalDate to, long count) {
}
