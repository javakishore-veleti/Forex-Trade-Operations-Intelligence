package com.fxtradeops.calendar.domain.calc;

import com.fxtradeops.calendar.domain.BookingDate;
import com.fxtradeops.calendar.domain.Cutoff;
import com.fxtradeops.calendar.domain.RegionCalendar;
import com.fxtradeops.domain.reference.RegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BookingDateCalculator — cutoff and booking-date classification (Req 6.2).
 * Uses fictional holidays and standard IANA zones.
 */
class BookingDateCalculatorTest {

    private static final RegionCalendar APAC_CALENDAR = new RegionCalendar(
            RegionCode.APAC,
            ZoneId.of("Asia/Singapore"),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(LocalDate.of(2025, 1, 1)), // FX-New-Dawn Day
            new Cutoff(LocalTime.of(17, 0))
    );

    private static final RegionCalendar EMEA_CALENDAR = new RegionCalendar(
            RegionCode.EMEA,
            ZoneId.of("Europe/London"),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(LocalDate.of(2025, 4, 18)), // FX-Spring Remembrance
            new Cutoff(LocalTime.of(17, 0))
    );

    @Nested
    @DisplayName("bookingDate()")
    class BookingDateTests {

        @Test
        @DisplayName("Before cutoff on a business day → same date (APAC)")
        void beforeCutoffOnBusinessDay_apac() {
            // Mon Jan 6 2025, 10:00 Singapore time
            Instant instant = ZonedDateTime.of(2025, 1, 6, 10, 0, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(APAC_CALENDAR, instant);
            assertEquals(LocalDate.of(2025, 1, 6), result.bookingDate());
        }

        @Test
        @DisplayName("At cutoff exactly on a business day → same date (APAC)")
        void atCutoffOnBusinessDay_apac() {
            // Mon Jan 6 2025, 17:00 Singapore time (at cutoff)
            Instant instant = ZonedDateTime.of(2025, 1, 6, 17, 0, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(APAC_CALENDAR, instant);
            assertEquals(LocalDate.of(2025, 1, 6), result.bookingDate());
        }

        @Test
        @DisplayName("After cutoff on a business day → next business day (APAC)")
        void afterCutoffOnBusinessDay_apac() {
            // Mon Jan 6 2025, 17:01 Singapore time (after cutoff)
            Instant instant = ZonedDateTime.of(2025, 1, 6, 17, 1, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(APAC_CALENDAR, instant);
            assertEquals(LocalDate.of(2025, 1, 7), result.bookingDate());
        }

        @Test
        @DisplayName("On a holiday → next business day (APAC)")
        void onHoliday_apac() {
            // Wed Jan 1 2025, 10:00 Singapore time (holiday)
            Instant instant = ZonedDateTime.of(2025, 1, 1, 10, 0, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(APAC_CALENDAR, instant);
            assertEquals(LocalDate.of(2025, 1, 2), result.bookingDate());
        }

        @Test
        @DisplayName("Before cutoff on a business day → same date (EMEA)")
        void beforeCutoffOnBusinessDay_emea() {
            // Mon Jan 6 2025, 14:00 London time
            Instant instant = ZonedDateTime.of(2025, 1, 6, 14, 0, 0, 0,
                    ZoneId.of("Europe/London")).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(EMEA_CALENDAR, instant);
            assertEquals(LocalDate.of(2025, 1, 6), result.bookingDate());
        }

        @Test
        @DisplayName("After cutoff on a business day → next business day (EMEA)")
        void afterCutoffOnBusinessDay_emea() {
            // Thu Apr 17, 2025 18:00 London time (after cutoff, next day is holiday)
            Instant instant = ZonedDateTime.of(2025, 4, 17, 18, 0, 0, 0,
                    ZoneId.of("Europe/London")).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(EMEA_CALENDAR, instant);
            // Apr 18 is holiday, so next BD is Mon Apr 21 (Sat+Sun skip)
            assertEquals(LocalDate.of(2025, 4, 21), result.bookingDate());
        }

        @Test
        @DisplayName("On a weekend → next business day")
        void onWeekend() {
            // Sat Jan 4 2025, 12:00 Singapore time
            Instant instant = ZonedDateTime.of(2025, 1, 4, 12, 0, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(APAC_CALENDAR, instant);
            assertEquals(LocalDate.of(2025, 1, 6), result.bookingDate());
        }
    }

    @Nested
    @DisplayName("isPostCutoff()")
    class IsPostCutoff {

        @Test
        @DisplayName("Before cutoff on business day → false")
        void beforeCutoff() {
            Instant instant = ZonedDateTime.of(2025, 1, 6, 16, 59, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            assertFalse(BookingDateCalculator.isPostCutoff(APAC_CALENDAR, instant));
        }

        @Test
        @DisplayName("After cutoff on business day → true")
        void afterCutoff() {
            Instant instant = ZonedDateTime.of(2025, 1, 6, 17, 1, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            assertTrue(BookingDateCalculator.isPostCutoff(APAC_CALENDAR, instant));
        }

        @Test
        @DisplayName("On weekend → true (no active business day)")
        void onWeekend() {
            Instant instant = ZonedDateTime.of(2025, 1, 4, 10, 0, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            assertTrue(BookingDateCalculator.isPostCutoff(APAC_CALENDAR, instant));
        }

        @Test
        @DisplayName("On holiday → true")
        void onHoliday() {
            Instant instant = ZonedDateTime.of(2025, 1, 1, 10, 0, 0, 0,
                    ZoneId.of("Asia/Singapore")).toInstant();
            assertTrue(BookingDateCalculator.isPostCutoff(APAC_CALENDAR, instant));
        }
    }
}
