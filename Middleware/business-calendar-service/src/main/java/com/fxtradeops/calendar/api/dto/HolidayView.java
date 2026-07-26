package com.fxtradeops.calendar.api.dto;

import java.time.LocalDate;

public record HolidayView(LocalDate date, String name) {
}
