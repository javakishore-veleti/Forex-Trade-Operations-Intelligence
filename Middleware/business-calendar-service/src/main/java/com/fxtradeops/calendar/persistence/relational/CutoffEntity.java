package com.fxtradeops.calendar.persistence.relational;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/**
 * JPA entity for the cutoff table. One cutoff per region.
 */
@Entity
@Table(name = "cutoff")
public class CutoffEntity {

    @Id
    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "cutoff_local_time", nullable = false)
    private LocalTime cutoffLocalTime;

    protected CutoffEntity() {
    }

    public CutoffEntity(String region, LocalTime cutoffLocalTime) {
        this.region = region;
        this.cutoffLocalTime = cutoffLocalTime;
    }

    public String getRegion() {
        return region;
    }

    public LocalTime getCutoffLocalTime() {
        return cutoffLocalTime;
    }
}
