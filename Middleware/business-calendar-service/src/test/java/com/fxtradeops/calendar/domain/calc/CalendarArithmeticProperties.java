package com.fxtradeops.calendar.domain.calc;

import com.fxtradeops.calendar.domain.Cutoff;
import com.fxtradeops.calendar.domain.RegionCalendar;
import com.fxtradeops.domain.reference.RegionCode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for calendar arithmetic invariants (Req 6.4, GP-Rq-13).
 * Asserts:
 * - addBusinessDays result is always a business day
 * - Round-trip +n/-n over business days
 * - businessDaysBetween >= 0 for from <= to
 * - Determinism: identical inputs yield identical answers
 */
class CalendarArithmeticProperties {

    private static final RegionCalendar APAC_CALENDAR = new RegionCalendar(
            RegionCode.APAC,
            ZoneId.of("Asia/Singapore"),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 5, 1),
                    LocalDate.of(2025, 8, 9),
                    LocalDate.of(2025, 12, 25)
            ),
            new Cutoff(LocalTime.of(17, 0))
    );

    private static final RegionCalendar EMEA_CALENDAR = new RegionCalendar(
            RegionCode.EMEA,
            ZoneId.of("Europe/London"),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 4, 18),
                    LocalDate.of(2025, 12, 25),
                    LocalDate.of(2025, 12, 26)
            ),
            new Cutoff(LocalTime.of(17, 0))
    );

    @Provide
    Arbitrary<LocalDate> dates() {
        return Arbitraries.integers().between(2025, 2025)
                .flatMap(year -> Arbitraries.integers().between(1, 12)
                        .flatMap(month -> Arbitraries.integers().between(1, 28)
                                .map(day -> LocalDate.of(year, month, day))));
    }

    @Property(tries = 200)
    void addBusinessDays_resultIsAlwaysABusinessDay(
            @ForAll("dates") LocalDate startDate,
            @ForAll @IntRange(min = 1, max = 30) int n) {
        LocalDate result = BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, startDate, n);
        assertTrue(BusinessDayCalculator.isBusinessDay(APAC_CALENDAR, result),
                "Result of addBusinessDays(" + startDate + ", " + n + ") = " + result + " is not a business day");
    }

    @Property(tries = 200)
    void addBusinessDays_negativeResultIsAlwaysABusinessDay(
            @ForAll("dates") LocalDate startDate,
            @ForAll @IntRange(min = 1, max = 30) int n) {
        LocalDate result = BusinessDayCalculator.addBusinessDays(EMEA_CALENDAR, startDate, -n);
        assertTrue(BusinessDayCalculator.isBusinessDay(EMEA_CALENDAR, result),
                "Result of addBusinessDays(" + startDate + ", -" + n + ") = " + result + " is not a business day");
    }

    @Property(tries = 200)
    void addBusinessDays_roundTrip(
            @ForAll("dates") LocalDate startDate,
            @ForAll @IntRange(min = 1, max = 20) int n) {
        // If we start from a business day, going +n then -n should return to the start
        if (BusinessDayCalculator.isBusinessDay(APAC_CALENDAR, startDate)) {
            LocalDate forward = BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, startDate, n);
            LocalDate backAgain = BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, forward, -n);
            assertEquals(startDate, backAgain,
                    "Round-trip failed: " + startDate + " +(" + n + ") = " + forward + " -(" + n + ") = " + backAgain);
        }
    }

    @Property(tries = 200)
    void businessDaysBetween_nonNegativeForOrderedDates(
            @ForAll("dates") LocalDate from,
            @ForAll @IntRange(min = 0, max = 60) int dayOffset) {
        LocalDate to = from.plusDays(dayOffset);
        long count = BusinessDayCalculator.businessDaysBetween(APAC_CALENDAR, from, to);
        assertTrue(count >= 0,
                "businessDaysBetween(" + from + ", " + to + ") = " + count + " is negative");
    }

    @Property(tries = 100)
    void determinism_identicalInputsYieldIdenticalResults(
            @ForAll("dates") LocalDate date,
            @ForAll @IntRange(min = 1, max = 15) int n) {
        // Same inputs, same reference data → same result (GP-Rq-13)
        LocalDate result1 = BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, date, n);
        LocalDate result2 = BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, date, n);
        assertEquals(result1, result2, "Determinism violation");

        long between1 = BusinessDayCalculator.businessDaysBetween(EMEA_CALENDAR, date, date.plusDays(n));
        long between2 = BusinessDayCalculator.businessDaysBetween(EMEA_CALENDAR, date, date.plusDays(n));
        assertEquals(between1, between2, "Determinism violation for businessDaysBetween");
    }
}
