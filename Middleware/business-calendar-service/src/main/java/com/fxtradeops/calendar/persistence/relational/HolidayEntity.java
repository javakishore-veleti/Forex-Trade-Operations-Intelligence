package com.fxtradeops.calendar.persistence.relational;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * JPA entity for the holiday table. Region-scoped.
 */
@Entity
@Table(name = "holiday")
public class HolidayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false)
    private String name;

    protected HolidayEntity() {
    }

    public HolidayEntity(String region, LocalDate holidayDate, String name) {
        this.region = region;
        this.holidayDate = holidayDate;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getRegion() {
        return region;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getName() {
        return name;
    }
}
