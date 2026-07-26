package com.fxtradeops.calendar.domain.calc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;

/**
 * Centralizes DST resolution rules:
 * - Spring-forward gap: push forward to first valid instant after the gap.
 * - Fall-back overlap: resolve to the earlier offset occurrence (withEarlierOffsetAtOverlap).
 *
 * These rules are applied uniformly to cutoff evaluation and booking-date classification.
 */
public final class DstResolver {

    private DstResolver() {
        // utility class
    }

    /**
     * Resolves a local date-time in the given zone, applying documented DST rules:
     * - Gap (spring-forward): pushes forward to the first valid instant after the gap.
     * - Overlap (fall-back): resolves to the earlier offset occurrence.
     */
    public static ZonedDateTime resolve(LocalDateTime localDateTime, ZoneId zone) {
        ZoneRules rules = zone.getRules();
        ZoneOffsetTransition transition = rules.getTransition(localDateTime);

        if (transition != null) {
            if (transition.isGap()) {
                // Spring-forward gap: push forward to the first valid instant
                LocalDateTime afterGap = transition.getDateTimeAfter();
                return ZonedDateTime.of(afterGap, zone);
            } else {
                // Fall-back overlap: resolve to earlier offset
                return ZonedDateTime.of(localDateTime, zone).withEarlierOffsetAtOverlap();
            }
        }

        // No transition — straightforward resolution
        return ZonedDateTime.of(localDateTime, zone).withEarlierOffsetAtOverlap();
    }
}
