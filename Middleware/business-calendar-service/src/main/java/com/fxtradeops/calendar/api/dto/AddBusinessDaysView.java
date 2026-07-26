package com.fxtradeops.calendar.api.dto;

import com.fxtradeops.domain.reference.RegionCode;

import java.time.LocalDate;

public record AddBusinessDaysView(RegionCode region, LocalDate from, int n, LocalDate result) {
}
