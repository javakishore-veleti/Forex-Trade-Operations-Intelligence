package com.fxtradeops.calendar.domain.calc;

import com.fxtradeops.calendar.domain.GlobalBusinessDate;
import com.fxtradeops.calendar.domain.RegionCalendar;
import com.fxtradeops.domain.reference.RegionCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Computes the global business date anchored to the base-country zone (GLOBAL → America/New_York).
 */
public final class GlobalBusinessDateCalculator {

    private GlobalBusinessDateCalculator() {
        // utility class
    }

    /**
     * Returns the GlobalBusinessDate for a given instant, using the GLOBAL region calendar
     * (base-country zone = America/New_York).
     */
    public static GlobalBusinessDate globalBusinessDate(RegionCalendar globalCalendar, Instant instant) {
        ZonedDateTime local = instant.atZone(globalCalendar.zone());
        LocalDate localDate = local.toLocalDate();
        LocalTime localTime = local.toLocalTime();
        LocalTime cutoffTime = globalCalendar.cutoff().localTime();

        LocalDate businessDate;
        if (BusinessDayCalculator.isBusinessDay(globalCalendar, localDate)
                && !localTime.isAfter(cutoffTime)) {
            businessDate = localDate;
        } else {
            businessDate = BusinessDayCalculator.addBusinessDays(globalCalendar, localDate, 1);
        }

        return new GlobalBusinessDate(instant, businessDate, globalCalendar.zone());
    }
}
