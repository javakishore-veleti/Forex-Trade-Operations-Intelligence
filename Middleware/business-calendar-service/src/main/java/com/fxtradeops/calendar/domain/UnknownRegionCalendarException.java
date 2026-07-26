package com.fxtradeops.calendar.domain;

import com.fxtradeops.domain.reference.RegionCode;

/**
 * Thrown when a query references a region with no configured RegionCalendar.
 */
public class UnknownRegionCalendarException extends RuntimeException {

    private final RegionCode region;

    public UnknownRegionCalendarException(RegionCode region) {
        super("No calendar configured for region: " + region);
        this.region = region;
    }

    public UnknownRegionCalendarException(String regionValue) {
        super("Invalid or unknown region: " + regionValue);
        this.region = null;
    }

    public RegionCode getRegion() {
        return region;
    }
}
