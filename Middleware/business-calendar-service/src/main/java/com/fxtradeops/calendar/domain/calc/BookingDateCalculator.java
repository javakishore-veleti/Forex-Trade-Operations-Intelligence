package com.fxtradeops.calendar.domain.calc;

import com.fxtradeops.calendar.domain.BookingDate;
import com.fxtradeops.calendar.domain.RegionCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Converts an Instant to the region's local time and applies the cutoff + business-day rules
 * to derive the BookingDate.
 */
public final class BookingDateCalculator {

    private BookingDateCalculator() {
        // utility class
    }

    /**
     * Computes the booking date for the given instant in the given region.
     *
     * Rules:
     * 1. Convert instant to region local time (DST-aware).
     * 2. If the local date is a business day AND local time <= cutoff → booking date is that date.
     * 3. Otherwise → booking date is the next business day.
     */
    public static BookingDate bookingDate(RegionCalendar calendar, Instant instant) {
        ZonedDateTime local = instant.atZone(calendar.zone());
        LocalDate localDate = local.toLocalDate();
        LocalTime localTime = local.toLocalTime();
        LocalTime cutoffTime = calendar.cutoff().localTime();

        if (BusinessDayCalculator.isBusinessDay(calendar, localDate)
                && !localTime.isAfter(cutoffTime)) {
            // At or before cutoff on a business day
            return new BookingDate(calendar.region(), instant, localDate);
        } else {
            // After cutoff or non-business day: roll forward to next business day
            LocalDate nextBd = BusinessDayCalculator.addBusinessDays(calendar, localDate, 1);
            return new BookingDate(calendar.region(), instant, nextBd);
        }
    }

    /**
     * Returns whether the given instant is after the current business date's cutoff for the region.
     */
    public static boolean isPostCutoff(RegionCalendar calendar, Instant instant) {
        ZonedDateTime local = instant.atZone(calendar.zone());
        LocalDate localDate = local.toLocalDate();
        LocalTime localTime = local.toLocalTime();
        LocalTime cutoffTime = calendar.cutoff().localTime();

        // If today is a business day, check if we're past the cutoff
        if (BusinessDayCalculator.isBusinessDay(calendar, localDate)) {
            return localTime.isAfter(cutoffTime);
        }
        // If it's not a business day, it's effectively "post-cutoff" (no active business day)
        return true;
    }
}
