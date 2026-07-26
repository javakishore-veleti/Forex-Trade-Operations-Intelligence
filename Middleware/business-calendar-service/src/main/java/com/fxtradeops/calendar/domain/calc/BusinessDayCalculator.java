package com.fxtradeops.calendar.domain.calc;

import com.fxtradeops.calendar.domain.BusinessDayReason;
import com.fxtradeops.calendar.domain.RegionCalendar;

import java.time.LocalDate;

/**
 * Pure functions for business-day classification and arithmetic.
 * Operates over a RegionCalendar — unit- and property-testable in isolation.
 */
public final class BusinessDayCalculator {

    private BusinessDayCalculator() {
        // utility class
    }

    /**
     * Classifies a date for the given calendar as WEEKEND, HOLIDAY, or BUSINESS_DAY.
     */
    public static BusinessDayReason classify(RegionCalendar calendar, LocalDate date) {
        if (calendar.weekend().contains(date.getDayOfWeek())) {
            return BusinessDayReason.WEEKEND;
        }
        if (calendar.holidays().contains(date)) {
            return BusinessDayReason.HOLIDAY;
        }
        return BusinessDayReason.BUSINESS_DAY;
    }

    /**
     * Returns true if the date is a business day for the given calendar.
     */
    public static boolean isBusinessDay(RegionCalendar calendar, LocalDate date) {
        return classify(calendar, date) == BusinessDayReason.BUSINESS_DAY;
    }

    /**
     * Returns the date that is n business days after (or before, for negative n) a given date.
     * n=0 returns the date unchanged.
     */
    public static LocalDate addBusinessDays(RegionCalendar calendar, LocalDate date, int n) {
        if (n == 0) {
            return date;
        }

        int direction = n > 0 ? 1 : -1;
        int remaining = Math.abs(n);
        LocalDate current = date;

        while (remaining > 0) {
            current = current.plusDays(direction);
            if (isBusinessDay(calendar, current)) {
                remaining--;
            }
        }

        return current;
    }

    /**
     * Returns the count of business days in the half-open interval [from, to).
     */
    public static long businessDaysBetween(RegionCalendar calendar, LocalDate from, LocalDate to) {
        if (!from.isBefore(to)) {
            return 0;
        }

        long count = 0;
        LocalDate current = from;
        while (current.isBefore(to)) {
            if (isBusinessDay(calendar, current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }
}
