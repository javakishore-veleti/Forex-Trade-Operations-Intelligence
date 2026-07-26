package com.fxtradeops.calendar.application;

import com.fxtradeops.calendar.config.ObservabilityConfig.CalendarMetrics;
import com.fxtradeops.calendar.domain.BookingDate;
import com.fxtradeops.calendar.domain.BusinessDayReason;
import com.fxtradeops.calendar.domain.CalendarRegistry;
import com.fxtradeops.calendar.domain.GlobalBusinessDate;
import com.fxtradeops.calendar.domain.Holiday;
import com.fxtradeops.calendar.domain.RegionCalendar;
import com.fxtradeops.calendar.domain.calc.BookingDateCalculator;
import com.fxtradeops.calendar.domain.calc.BusinessDayCalculator;
import com.fxtradeops.calendar.domain.calc.GlobalBusinessDateCalculator;
import com.fxtradeops.calendar.persistence.relational.HolidayEntity;
import com.fxtradeops.calendar.persistence.relational.HolidayRepository;
import com.fxtradeops.domain.reference.RegionCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Application service orchestrating CalendarRegistry + domain calculators.
 */
@Service
public class CalendarQueryService {

    private final CalendarRegistry registry;
    private final HolidayRepository holidayRepository;
    private final CalendarMetrics metrics;

    public CalendarQueryService(CalendarRegistry registry,
                                HolidayRepository holidayRepository,
                                CalendarMetrics metrics) {
        this.registry = registry;
        this.holidayRepository = holidayRepository;
        this.metrics = metrics;
    }

    public BusinessDayReason classifyBusinessDay(RegionCode region, LocalDate date) {
        RegionCalendar cal = registry.calendarFor(region);
        BusinessDayReason reason = BusinessDayCalculator.classify(cal, date);
        metrics.recordBusinessDay(region.name(), reason.name());
        return reason;
    }

    public boolean isBusinessDay(RegionCode region, LocalDate date) {
        RegionCalendar cal = registry.calendarFor(region);
        return BusinessDayCalculator.isBusinessDay(cal, date);
    }

    public LocalDate addBusinessDays(RegionCode region, LocalDate date, int n) {
        RegionCalendar cal = registry.calendarFor(region);
        return BusinessDayCalculator.addBusinessDays(cal, date, n);
    }

    public long businessDaysBetween(RegionCode region, LocalDate from, LocalDate to) {
        RegionCalendar cal = registry.calendarFor(region);
        return BusinessDayCalculator.businessDaysBetween(cal, from, to);
    }

    public List<Holiday> holidaysForYear(RegionCode region, int year) {
        // Validate region has a calendar
        registry.calendarFor(region);

        return holidayRepository.findByRegion(region.name()).stream()
                .filter(h -> h.getHolidayDate().getYear() == year)
                .map(h -> new Holiday(h.getHolidayDate(), h.getName()))
                .toList();
    }

    public BookingDate bookingDate(RegionCode region, Instant instant) {
        RegionCalendar cal = registry.calendarFor(region);
        BookingDate result = BookingDateCalculator.bookingDate(cal, instant);
        metrics.recordBookingDate(region.name());
        return result;
    }

    public boolean isPostCutoff(RegionCode region, Instant instant) {
        RegionCalendar cal = registry.calendarFor(region);
        return BookingDateCalculator.isPostCutoff(cal, instant);
    }

    public GlobalBusinessDate globalBusinessDate(Instant instant) {
        RegionCalendar globalCal = registry.calendarFor(RegionCode.GLOBAL);
        return GlobalBusinessDateCalculator.globalBusinessDate(globalCal, instant);
    }
}
