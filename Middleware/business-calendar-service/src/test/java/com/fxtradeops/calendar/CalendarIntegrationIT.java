package com.fxtradeops.calendar;

import com.fxtradeops.calendar.application.CalendarQueryService;
import com.fxtradeops.calendar.domain.BookingDate;
import com.fxtradeops.calendar.domain.BusinessDayReason;
import com.fxtradeops.calendar.domain.GlobalBusinessDate;
import com.fxtradeops.calendar.domain.Holiday;
import com.fxtradeops.domain.reference.RegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test with Testcontainers PostgreSQL — loads reference data from Flyway migrations,
 * verifies registry population, and exercises booking-date/business-day queries end-to-end (Req 6.2).
 * All fixtures use fictional holidays, standard IANA zones, synthetic FX- ids (Req 6.5, GP-Rq-14).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class CalendarIntegrationIT {

    @Autowired
    private CalendarQueryService queryService;

    @Test
    @DisplayName("Registry loaded — APAC business day classification works end-to-end")
    void apacBusinessDayClassification() {
        // 2025-01-06 is a Monday, not a holiday
        BusinessDayReason reason = queryService.classifyBusinessDay(RegionCode.APAC, LocalDate.of(2025, 1, 6));
        assertEquals(BusinessDayReason.BUSINESS_DAY, reason);
    }

    @Test
    @DisplayName("Registry loaded — APAC holiday classified correctly")
    void apacHolidayClassification() {
        // 2025-01-01 is FX-New-Dawn Day (fictional holiday)
        BusinessDayReason reason = queryService.classifyBusinessDay(RegionCode.APAC, LocalDate.of(2025, 1, 1));
        assertEquals(BusinessDayReason.HOLIDAY, reason);
    }

    @Test
    @DisplayName("Registry loaded — EMEA weekend classified correctly")
    void emeaWeekendClassification() {
        // 2025-01-04 is a Saturday
        BusinessDayReason reason = queryService.classifyBusinessDay(RegionCode.EMEA, LocalDate.of(2025, 1, 4));
        assertEquals(BusinessDayReason.WEEKEND, reason);
    }

    @Test
    @DisplayName("APAC booking date before cutoff")
    void apacBookingDateBeforeCutoff() {
        // Mon Jan 6, 2025 10:00 Singapore time (before 17:00 cutoff)
        Instant instant = ZonedDateTime.of(2025, 1, 6, 10, 0, 0, 0,
                ZoneId.of("Asia/Singapore")).toInstant();
        BookingDate result = queryService.bookingDate(RegionCode.APAC, instant);
        assertEquals(LocalDate.of(2025, 1, 6), result.bookingDate());
    }

    @Test
    @DisplayName("APAC booking date after cutoff rolls to next business day")
    void apacBookingDateAfterCutoff() {
        // Mon Jan 6, 2025 18:00 Singapore time (after 17:00 cutoff)
        Instant instant = ZonedDateTime.of(2025, 1, 6, 18, 0, 0, 0,
                ZoneId.of("Asia/Singapore")).toInstant();
        BookingDate result = queryService.bookingDate(RegionCode.APAC, instant);
        assertEquals(LocalDate.of(2025, 1, 7), result.bookingDate());
    }

    @Test
    @DisplayName("EMEA booking date on holiday rolls to next business day")
    void emeaBookingDateOnHoliday() {
        // Apr 18, 2025 (FX-Spring Remembrance) 10:00 London time
        Instant instant = ZonedDateTime.of(2025, 4, 18, 10, 0, 0, 0,
                ZoneId.of("Europe/London")).toInstant();
        BookingDate result = queryService.bookingDate(RegionCode.EMEA, instant);
        // Apr 18 is holiday, Apr 19 Sat, Apr 20 Sun, Apr 21 Mon (also a holiday in EMEA seed)
        // Check: Apr 21 is FX-Renewal Monday in seed data — so next BD is Apr 22
        assertEquals(LocalDate.of(2025, 4, 22), result.bookingDate());
    }

    @Test
    @DisplayName("Global business date computation works end-to-end")
    void globalBusinessDate() {
        // Mon Jan 6, 2025 14:00 New York time (before 17:00 cutoff)
        Instant instant = ZonedDateTime.of(2025, 1, 6, 14, 0, 0, 0,
                ZoneId.of("America/New_York")).toInstant();
        GlobalBusinessDate result = queryService.globalBusinessDate(instant);
        assertEquals(LocalDate.of(2025, 1, 6), result.globalBusinessDate());
        assertEquals(ZoneId.of("America/New_York"), result.anchorZone());
    }

    @Test
    @DisplayName("Holidays list for APAC 2025 returns seeded data")
    void apacHolidays2025() {
        List<Holiday> holidays = queryService.holidaysForYear(RegionCode.APAC, 2025);
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.stream().allMatch(h -> h.name().startsWith("FX-")));
    }

    @Test
    @DisplayName("Add business days — AMERICAS forward across weekend")
    void americasAddBusinessDays() {
        // Fri Jan 3, 2025 + 1 BD = Mon Jan 6 (skip Sat/Sun)
        LocalDate result = queryService.addBusinessDays(RegionCode.AMERICAS, LocalDate.of(2025, 1, 3), 1);
        assertEquals(LocalDate.of(2025, 1, 6), result);
    }

    @Test
    @DisplayName("Business days between — EMEA week count")
    void emeaBusinessDaysBetween() {
        // Mon Jan 6 to Fri Jan 10: 4 business days in [from, to)
        long count = queryService.businessDaysBetween(RegionCode.EMEA,
                LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10));
        assertEquals(4, count);
    }

    @Test
    @DisplayName("isPostCutoff — before cutoff is false")
    void isPostCutoffBefore() {
        Instant instant = ZonedDateTime.of(2025, 1, 6, 16, 0, 0, 0,
                ZoneId.of("America/New_York")).toInstant();
        assertFalse(queryService.isPostCutoff(RegionCode.AMERICAS, instant));
    }

    @Test
    @DisplayName("isPostCutoff — after cutoff is true")
    void isPostCutoffAfter() {
        Instant instant = ZonedDateTime.of(2025, 1, 6, 18, 0, 0, 0,
                ZoneId.of("America/New_York")).toInstant();
        assertTrue(queryService.isPostCutoff(RegionCode.AMERICAS, instant));
    }
}
