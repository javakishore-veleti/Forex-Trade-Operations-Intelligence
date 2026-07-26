package com.fxtradeops.calendar.persistence.relational;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * JPA entity for the region_calendar table.
 */
@Entity
@Table(name = "region_calendar")
public class RegionCalendarEntity {

    @Id
    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "iana_zone", nullable = false)
    private String ianaZone;

    @Column(name = "weekend_days", nullable = false)
    private String weekendDays;

    @Version
    @Column(name = "version")
    private Long version;

    protected RegionCalendarEntity() {
    }

    public RegionCalendarEntity(String region, String ianaZone, String weekendDays) {
        this.region = region;
        this.ianaZone = ianaZone;
        this.weekendDays = weekendDays;
    }

    public String getRegion() {
        return region;
    }

    public String getIanaZone() {
        return ianaZone;
    }

    public String getWeekendDays() {
        return weekendDays;
    }

    public Long getVersion() {
        return version;
    }
}
