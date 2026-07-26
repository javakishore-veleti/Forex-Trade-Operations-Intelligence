package com.fxtradeops.tradeingest.domain;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Pure function: determines whether a given trade date is within a specified number of business days
 * from today. Weekends (Saturday, Sunday) and a configurable set of fictional holidays are skipped.
 */
@Component
public class BusinessDayValidator {

    private final Set<LocalDate> holidays;

    public BusinessDayValidator() {
        // Configurable fictional holiday set
        this.holidays = Set.of();
    }

    public BusinessDayValidator(Set<LocalDate> holidays) {
        this.holidays = holidays != null ? Set.copyOf(holidays) : Set.of();
    }

    /**
     * Checks whether the tradeDate is within maxBusinessDays business days in the past from today.
     *
     * @param tradeDate       the trade date to validate
     * @param today           the reference date (today)
     * @param maxBusinessDays the maximum number of business days allowed in the past
     * @return true if tradeDate is within the allowed business-day window
     */
    public boolean isWithinWindow(LocalDate tradeDate, LocalDate today, int maxBusinessDays) {
        if (tradeDate == null || today == null) {
            return false;
        }
        if (tradeDate.isAfter(today)) {
            // Trade date in the future is valid from a business-day window perspective
            return true;
        }

        // Count business days between tradeDate and today (exclusive of today)
        int businessDaysCount = 0;
        LocalDate cursor = tradeDate;
        while (cursor.isBefore(today)) {
            if (isBusinessDay(cursor)) {
                businessDaysCount++;
            }
            cursor = cursor.plusDays(1);
        }

        return businessDaysCount <= maxBusinessDays;
    }

    private boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }
}
