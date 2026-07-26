package com.fxtradeops.calendar.api;

import com.fxtradeops.calendar.api.dto.AddBusinessDaysView;
import com.fxtradeops.calendar.api.dto.BookingDateView;
import com.fxtradeops.calendar.api.dto.BusinessDayView;
import com.fxtradeops.calendar.api.dto.BusinessDaysBetweenView;
import com.fxtradeops.calendar.api.dto.GlobalBusinessDateView;
import com.fxtradeops.calendar.api.dto.HolidayView;
import com.fxtradeops.calendar.api.dto.PostCutoffView;
import com.fxtradeops.calendar.application.CalendarQueryService;
import com.fxtradeops.calendar.domain.BookingDate;
import com.fxtradeops.calendar.domain.BusinessDayReason;
import com.fxtradeops.calendar.domain.GlobalBusinessDate;
import com.fxtradeops.calendar.domain.Holiday;
import com.fxtradeops.domain.reference.RegionCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only REST API for calendar queries.
 * All endpoints are side-effect free (GP-Rq-1.4).
 */
@RestController
@RequestMapping("/api/v1/calendars")
public class CalendarQueryController {

    private final CalendarQueryService queryService;

    public CalendarQueryController(CalendarQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{region}/business-day")
    public ResponseEntity<BusinessDayView> businessDay(
            @PathVariable("region") String region,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        RegionCode regionCode = parseRegion(region);
        BusinessDayReason reason = queryService.classifyBusinessDay(regionCode, date);
        boolean isBusinessDay = reason == BusinessDayReason.BUSINESS_DAY;

        return ResponseEntity.ok(new BusinessDayView(regionCode, date, isBusinessDay, reason.name()));
    }

    @GetMapping("/{region}/add-business-days")
    public ResponseEntity<AddBusinessDaysView> addBusinessDays(
            @PathVariable("region") String region,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("n") int n) {

        RegionCode regionCode = parseRegion(region);
        LocalDate result = queryService.addBusinessDays(regionCode, date, n);

        return ResponseEntity.ok(new AddBusinessDaysView(regionCode, date, n, result));
    }

    @GetMapping("/{region}/business-days-between")
    public ResponseEntity<BusinessDaysBetweenView> businessDaysBetween(
            @PathVariable("region") String region,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        RegionCode regionCode = parseRegion(region);
        long count = queryService.businessDaysBetween(regionCode, from, to);

        return ResponseEntity.ok(new BusinessDaysBetweenView(regionCode, from, to, count));
    }

    @GetMapping("/{region}/holidays")
    public ResponseEntity<List<HolidayView>> holidays(
            @PathVariable("region") String region,
            @RequestParam("year") int year) {

        RegionCode regionCode = parseRegion(region);
        List<Holiday> holidays = queryService.holidaysForYear(regionCode, year);
        List<HolidayView> views = holidays.stream()
                .map(h -> new HolidayView(h.date(), h.name()))
                .toList();

        return ResponseEntity.ok(views);
    }

    @GetMapping("/{region}/booking-date")
    public ResponseEntity<BookingDateView> bookingDate(
            @PathVariable("region") String region,
            @RequestParam("instant") Instant instant) {

        RegionCode regionCode = parseRegion(region);
        BookingDate result = queryService.bookingDate(regionCode, instant);

        return ResponseEntity.ok(new BookingDateView(regionCode, instant, result.bookingDate()));
    }

    @GetMapping("/{region}/post-cutoff")
    public ResponseEntity<PostCutoffView> postCutoff(
            @PathVariable("region") String region,
            @RequestParam("instant") Instant instant) {

        RegionCode regionCode = parseRegion(region);
        boolean postCutoff = queryService.isPostCutoff(regionCode, instant);

        return ResponseEntity.ok(new PostCutoffView(regionCode, instant, postCutoff));
    }

    @GetMapping("/global/business-date")
    public ResponseEntity<GlobalBusinessDateView> globalBusinessDate(
            @RequestParam("instant") Instant instant) {

        GlobalBusinessDate result = queryService.globalBusinessDate(instant);

        return ResponseEntity.ok(new GlobalBusinessDateView(
                result.instant(), result.globalBusinessDate(), result.anchorZone()));
    }

    private RegionCode parseRegion(String region) {
        try {
            return RegionCode.valueOf(region.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid region code: " + region);
        }
    }
}
