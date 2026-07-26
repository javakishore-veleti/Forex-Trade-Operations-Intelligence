package com.fxtradeops.calendar.domain;

import com.fxtradeops.domain.reference.RegionCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Immutable in-memory authoritative view of all region calendars.
 * Built once at startup from the relational store and never mutated after that.
 * Uses an internal AtomicReference so the bean can be wired before data is loaded,
 * then populated once at startup.
 */
public class CalendarRegistry {

    private final AtomicReference<Map<RegionCode, RegionCalendar>> calendarsRef = new AtomicReference<>();

    /**
     * Populates the registry. Must be called exactly once at startup.
     */
    public void initialize(Map<RegionCode, RegionCalendar> calendars) {
        if (!calendarsRef.compareAndSet(null, Collections.unmodifiableMap(new EnumMap<>(calendars)))) {
            throw new IllegalStateException("CalendarRegistry already initialized");
        }
    }

    /**
     * Returns the calendar for the given region.
     *
     * @throws UnknownRegionCalendarException if no calendar is configured for the region
     * @throws IllegalStateException          if the registry has not been initialized
     */
    public RegionCalendar calendarFor(RegionCode region) {
        Map<RegionCode, RegionCalendar> calendars = calendarsRef.get();
        if (calendars == null) {
            throw new IllegalStateException("Calendar registry not yet loaded");
        }
        RegionCalendar cal = calendars.get(region);
        if (cal == null) {
            throw new UnknownRegionCalendarException(region);
        }
        return cal;
    }

    /**
     * Returns true if the registry has been populated with at least one calendar.
     */
    public boolean isLoaded() {
        Map<RegionCode, RegionCalendar> calendars = calendarsRef.get();
        return calendars != null && !calendars.isEmpty();
    }

    /**
     * Returns all loaded region codes.
     */
    public Map<RegionCode, RegionCalendar> allCalendars() {
        Map<RegionCode, RegionCalendar> calendars = calendarsRef.get();
        return calendars != null ? calendars : Map.of();
    }
}
