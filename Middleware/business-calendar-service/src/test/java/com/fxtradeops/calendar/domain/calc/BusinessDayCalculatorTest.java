package com.fxtradeops.calendar.domain.calc;

import com.fxtradeops.calendar.domain.BusinessDayReason;
import com.fxtradeops.calendar.domain.Cutoff;
import com.fxtradeops.calendar.domain.RegionCalendar;
import com.fxtradeops.domain.reference.RegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BusinessDayCalculator — classification and arithmetic.
 * Uses fictional holidays and standard IANA zones (Req 6.1, 6.5).
 */
class BusinessDayCalculatorTest {

    // APAC region: Asia/Singapore, no DST
    private static final RegionCalendar APAC_CALENDAR = new RegionCalendar(
            RegionCode.APAC,
            ZoneId.of("Asia/Singapore"),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(
                    LocalDate.of(2025, 1, 1),   // FX-New-Dawn Day
                    LocalDate.of(2025, 5, 1),   // FX-Labour Unity Day
                    LocalDate.of(2025, 12, 25)  // FX-Year End Harmony Day
            ),
            new Cutoff(LocalTime.of(17, 0))
    );

    // EMEA region: Europe/London, observes DST
    private static final RegionCalendar EMEA_CALENDAR = new RegionCalendar(
            RegionCode.EMEA,
            ZoneId.of("Europe/London"),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(
                    LocalDate.of(2025, 1, 1),   // FX-New Year Unity
                    LocalDate.of(2025, 4, 18),  // FX-Spring Remembrance
                    LocalDate.of(2025, 12, 25)  // FX-Solstice Day
            ),
            new Cutoff(LocalTime.of(17, 0))
    );

    // AMERICAS region: America/New_York, observes DST
    private static final RegionCalendar AMERICAS_CALENDAR = new RegionCalendar(
            RegionCode.AMERICAS,
            ZoneId.of("America/New_York"),
            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            Set.of(
                    LocalDate.of(2025, 1, 1),   // FX-New Epoch Day
                    LocalDate.of(2025, 7, 4),   // FX-Freedom Day
                    LocalDate.of(2025, 12, 25)  // FX-Solstice Celebration
            ),
            new Cutoff(LocalTime.of(17, 0))
    );

    @Nested
    @DisplayName("classify()")
    class Classify {

        @Test
        @DisplayName("Saturday is classified as WEEKEND for APAC")
        void saturdayIsWeekend() {
            // 2025-01-04 is a Saturday
            assertEquals(BusinessDayReason.WEEKEND,
                    BusinessDayCalculator.classify(APAC_CALENDAR, LocalDate.of(2025, 1, 4)));
        }

        @Test
        @DisplayName("Sunday is classified as WEEKEND for EMEA")
        void sundayIsWeekend() {
            // 2025-01-05 is a Sunday
            assertEquals(BusinessDayReason.WEEKEND,
                    BusinessDayCalculator.classify(EMEA_CALENDAR, LocalDate.of(2025, 1, 5)));
        }

        @Test
        @DisplayName("Configured holiday is classified as HOLIDAY for APAC")
        void holidayForApac() {
            assertEquals(BusinessDayReason.HOLIDAY,
                    BusinessDayCalculator.classify(APAC_CALENDAR, LocalDate.of(2025, 1, 1)));
        }

        @Test
        @DisplayName("Configured holiday is classified as HOLIDAY for EMEA")
        void holidayForEmea() {
            assertEquals(BusinessDayReason.HOLIDAY,
                    BusinessDayCalculator.classify(EMEA_CALENDAR, LocalDate.of(2025, 4, 18)));
        }

        @Test
        @DisplayName("Ordinary weekday is classified as BUSINESS_DAY for APAC")
        void businessDayApac() {
            // 2025-01-06 is a Monday, not a holiday
            assertEquals(BusinessDayReason.BUSINESS_DAY,
                    BusinessDayCalculator.classify(APAC_CALENDAR, LocalDate.of(2025, 1, 6)));
        }

        @Test
        @DisplayName("Ordinary weekday is classified as BUSINESS_DAY for AMERICAS")
        void businessDayAmericas() {
            // 2025-01-02 is a Thursday, not a holiday
            assertEquals(BusinessDayReason.BUSINESS_DAY,
                    BusinessDayCalculator.classify(AMERICAS_CALENDAR, LocalDate.of(2025, 1, 2)));
        }

        @Test
        @DisplayName("Holiday scoped to APAC does not affect EMEA")
        void holidayScopedPerRegion() {
            // May 1 is holiday for APAC but not EMEA
            assertEquals(BusinessDayReason.HOLIDAY,
                    BusinessDayCalculator.classify(APAC_CALENDAR, LocalDate.of(2025, 5, 1)));
            assertEquals(BusinessDayReason.BUSINESS_DAY,
                    BusinessDayCalculator.classify(EMEA_CALENDAR, LocalDate.of(2025, 5, 1)));
        }
    }

    @Nested
    @DisplayName("addBusinessDays()")
    class AddBusinessDays {

        @Test
        @DisplayName("n=0 returns the same date")
        void zeroReturnsUnchanged() {
            LocalDate date = LocalDate.of(2025, 1, 6); // Monday
            assertEquals(date, BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, date, 0));
        }

        @Test
        @DisplayName("Forward skips weekends")
        void forwardSkipsWeekends() {
            // Friday 2025-01-03 + 1 BD = Monday 2025-01-06
            assertEquals(LocalDate.of(2025, 1, 6),
                    BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, LocalDate.of(2025, 1, 3), 1));
        }

        @Test
        @DisplayName("Forward skips holidays")
        void forwardSkipsHolidays() {
            // 2024-12-31 + 1 BD should skip Jan 1 (holiday) → Jan 2
            assertEquals(LocalDate.of(2025, 1, 2),
                    BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, LocalDate.of(2024, 12, 31), 1));
        }

        @Test
        @DisplayName("Backward skips weekends")
        void backwardSkipsWeekends() {
            // Monday 2025-01-06 - 1 BD = Friday 2025-01-03
            assertEquals(LocalDate.of(2025, 1, 3),
                    BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, LocalDate.of(2025, 1, 6), -1));
        }

        @Test
        @DisplayName("Backward skips holidays")
        void backwardSkipsHolidays() {
            // 2025-01-02 - 1 BD should skip Jan 1 (holiday) → Dec 31
            assertEquals(LocalDate.of(2024, 12, 31),
                    BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, LocalDate.of(2025, 1, 2), -1));
        }

        @Test
        @DisplayName("Multiple business days forward across weekend and holiday")
        void multipleDaysForward() {
            // Starting Thursday Jan 2, add 3 BD:
            // Jan 3 (Fri, BD), skip Sat+Sun, Jan 6 (Mon, BD), Jan 7 (Tue, BD)
            assertEquals(LocalDate.of(2025, 1, 7),
                    BusinessDayCalculator.addBusinessDays(APAC_CALENDAR, LocalDate.of(2025, 1, 2), 3));
        }
    }

    @Nested
    @DisplayName("businessDaysBetween()")
    class BusinessDaysBetween {

        @Test
        @DisplayName("Half-open interval [from, to) counts correctly")
        void halfOpenInterval() {
            // Mon Jan 6 to Fri Jan 10: Mon, Tue, Wed, Thu = 4 days (Fri excluded)
            assertEquals(4,
                    BusinessDayCalculator.businessDaysBetween(APAC_CALENDAR,
                            LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10)));
        }

        @Test
        @DisplayName("Returns 0 when from >= to")
        void returnsZeroWhenFromAfterTo() {
            assertEquals(0,
                    BusinessDayCalculator.businessDaysBetween(APAC_CALENDAR,
                            LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 6)));
        }

        @Test
        @DisplayName("Same date returns 0")
        void sameDateReturnsZero() {
            assertEquals(0,
                    BusinessDayCalculator.businessDaysBetween(APAC_CALENDAR,
                            LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 6)));
        }

        @Test
        @DisplayName("Excludes weekends and holidays from count")
        void excludesWeekendsAndHolidays() {
            // Dec 31 to Jan 6 [Wed Dec 31, Thu Jan 1 (holiday), Fri Jan 2, Sat, Sun, Mon Jan 5... wait
            // 2025: Dec 31 is Tuesday (2024), Jan 1 Wed (holiday), Jan 2 Thu, Jan 3 Fri, Jan 4 Sat, Jan 5 Sun
            // [Dec 31, Jan 6): Dec 31(BD), Jan 1(H), Jan 2(BD), Jan 3(BD), Jan 4(WE), Jan 5(WE) = 3 BD
            assertEquals(3,
                    BusinessDayCalculator.businessDaysBetween(APAC_CALENDAR,
                            LocalDate.of(2024, 12, 31), LocalDate.of(2025, 1, 6)));
        }
    }
}
