package com.fxtradeops.calendar.domain;

import com.fxtradeops.domain.reference.RegionCode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;

/**
 * Immutable calendar definition for a single region — its IANA time zone,
 * weekend definition, holiday set, and cutoff.
 * The consistency boundary for all calendar answers about one region.
 */
public record RegionCalendar(
        RegionCode region,
        ZoneId zone,
        Set<DayOfWeek> weekend,
        Set<LocalDate> holidays,
        Cutoff cutoff) {

    public RegionCalendar {
        if (region == null) throw new IllegalArgumentException("region must not be null");
        if (zone == null) throw new IllegalArgumentException("zone must not be null");
        if (weekend == null) throw new IllegalArgumentException("weekend must not be null");
        if (holidays == null) throw new IllegalArgumentException("holidays must not be null");
        if (cutoff == null) throw new IllegalArgumentException("cutoff must not be null");
        weekend = Collections.unmodifiableSet(weekend);
        holidays = Collections.unmodifiableSet(holidays);
    }
}
