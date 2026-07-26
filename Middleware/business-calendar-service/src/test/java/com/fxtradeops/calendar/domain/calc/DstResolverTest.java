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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DST edge-case tests: spring-forward gap and fall-back overlap for
 * Europe/London and America/New_York (Req 6.3).
 * Verifies the documented deterministic rules:
 * - Gap: push forward to first valid instant
 * - Overlap: resolve to earlier offset (withEarlierOffsetAtOverlap)
 */
class DstResolverTest {

    @Nested
    @DisplayName("Spring-forward gap (Europe/London: last Sunday in March, 01:00 → 02:00 BST)")
    class SpringForwardGap {

        private static final ZoneId LONDON = ZoneId.of("Europe/London");

        @Test
        @DisplayName("Local time in gap is pushed forward to first valid instant")
        void gapPushesForward() {
            // 2025-03-30: clocks spring forward from 01:00 to 02:00
            // 01:30 does not exist — should resolve to 02:00 BST
            LocalDateTime gapTime = LocalDateTime.of(2025, 3, 30, 1, 30);
            ZonedDateTime resolved = DstResolver.resolve(gapTime, LONDON);

            // Should be 02:00 BST (UTC+1)
            assertEquals(LocalTime.of(2, 0), resolved.toLocalTime());
            assertEquals(LocalDate.of(2025, 3, 30), resolved.toLocalDate());
            // Offset should be +01:00 (BST)
            assertEquals(1, resolved.getOffset().getTotalSeconds() / 3600);
        }

        @Test
        @DisplayName("Booking date during spring-forward gap resolves correctly")
        void bookingDateDuringGap() {
            // Create a calendar with cutoff at 01:30 London time (which is in the gap on transition day)
            RegionCalendar emeaCalendar = new RegionCalendar(
                    RegionCode.EMEA,
                    LONDON,
                    Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    Set.of(),
                    new Cutoff(LocalTime.of(1, 30))
            );

            // An instant at 02:00 BST on the transition day (which is 01:00 UTC)
            // After the gap, so local time is 02:00 BST
            Instant instant = ZonedDateTime.of(2025, 3, 30, 2, 0, 0, 0, LONDON).toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(emeaCalendar, instant);

            // 02:00 > 01:30 cutoff, so should be next business day (Mon Mar 31)
            assertEquals(LocalDate.of(2025, 3, 31), result.bookingDate());
        }
    }

    @Nested
    @DisplayName("Fall-back overlap (America/New_York: first Sunday in November, 02:00 → 01:00 EST)")
    class FallBackOverlap {

        private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

        @Test
        @DisplayName("Local time in overlap resolves to earlier offset")
        void overlapResolvesToEarlierOffset() {
            // 2025-11-02: clocks fall back from 02:00 EDT to 01:00 EST
            // 01:30 occurs twice — should resolve to the earlier (EDT, -04:00) occurrence
            LocalDateTime overlapTime = LocalDateTime.of(2025, 11, 2, 1, 30);
            ZonedDateTime resolved = DstResolver.resolve(overlapTime, NEW_YORK);

            // Should be the earlier offset (EDT = -04:00)
            assertEquals(-4, resolved.getOffset().getTotalSeconds() / 3600);
            assertEquals(LocalTime.of(1, 30), resolved.toLocalTime());
        }

        @Test
        @DisplayName("Booking date during fall-back overlap uses earlier offset")
        void bookingDateDuringOverlap() {
            // 2025-11-02 is a Sunday, so it's a weekend anyway
            // Let's use a hypothetical scenario with no weekends to test pure overlap logic
            RegionCalendar calNoWeekends = new RegionCalendar(
                    RegionCode.AMERICAS,
                    NEW_YORK,
                    Set.of(), // no weekends for this test
                    Set.of(),
                    new Cutoff(LocalTime.of(17, 0))
            );

            // Instant at 01:30 EDT (the first occurrence, before fall-back)
            // EDT is UTC-4, so 01:30 EDT = 05:30 UTC
            Instant instant = ZonedDateTime.of(2025, 11, 2, 1, 30, 0, 0, NEW_YORK)
                    .withEarlierOffsetAtOverlap().toInstant();
            BookingDate result = BookingDateCalculator.bookingDate(calNoWeekends, instant);

            // 01:30 < 17:00 cutoff and Nov 2 is a business day (no weekends) → same date
            assertEquals(LocalDate.of(2025, 11, 2), result.bookingDate());
        }
    }

    @Nested
    @DisplayName("Non-DST zone (Asia/Singapore) — no transitions")
    class NoDst {

        private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

        @Test
        @DisplayName("Resolution is straightforward with no gap or overlap")
        void noTransition() {
            LocalDateTime localTime = LocalDateTime.of(2025, 6, 15, 10, 30);
            ZonedDateTime resolved = DstResolver.resolve(localTime, SINGAPORE);

            assertEquals(LocalTime.of(10, 30), resolved.toLocalTime());
            assertEquals(LocalDate.of(2025, 6, 15), resolved.toLocalDate());
            assertEquals(8, resolved.getOffset().getTotalSeconds() / 3600);
        }
    }
}
