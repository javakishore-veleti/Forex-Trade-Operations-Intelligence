package com.fxtradeops.calendar.config;

import com.fxtradeops.calendar.domain.CalendarRegistry;
import com.fxtradeops.calendar.domain.Cutoff;
import com.fxtradeops.calendar.domain.RegionCalendar;
import com.fxtradeops.calendar.persistence.relational.CutoffEntity;
import com.fxtradeops.calendar.persistence.relational.CutoffRepository;
import com.fxtradeops.calendar.persistence.relational.HolidayEntity;
import com.fxtradeops.calendar.persistence.relational.HolidayRepository;
import com.fxtradeops.calendar.persistence.relational.RegionCalendarEntity;
import com.fxtradeops.calendar.persistence.relational.RegionCalendarRepository;
import com.fxtradeops.domain.reference.RegionCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads reference data from PostgreSQL at startup and builds the immutable CalendarRegistry.
 * Readiness stays DOWN until the load completes.
 */
@Configuration
public class CalendarBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(CalendarBootstrapConfig.class);

    private final RegionCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final CutoffRepository cutoffRepository;

    public CalendarBootstrapConfig(RegionCalendarRepository calendarRepository,
                                   HolidayRepository holidayRepository,
                                   CutoffRepository cutoffRepository) {
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
        this.cutoffRepository = cutoffRepository;
    }

    @Bean
    public CalendarRegistry calendarRegistry() {
        return new CalendarRegistry();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadCalendarData() {
        log.info("Loading calendar reference data from database...");

        List<RegionCalendarEntity> regionEntities = calendarRepository.findAll();
        List<HolidayEntity> holidayEntities = holidayRepository.findAll();
        List<CutoffEntity> cutoffEntities = cutoffRepository.findAll();

        // Index cutoffs by region
        Map<String, CutoffEntity> cutoffsByRegion = cutoffEntities.stream()
                .collect(Collectors.toMap(CutoffEntity::getRegion, e -> e));

        // Index holidays by region
        Map<String, Set<LocalDate>> holidaysByRegion = holidayEntities.stream()
                .collect(Collectors.groupingBy(
                        HolidayEntity::getRegion,
                        Collectors.mapping(HolidayEntity::getHolidayDate, Collectors.toSet())));

        Map<RegionCode, RegionCalendar> calendars = new EnumMap<>(RegionCode.class);

        for (RegionCalendarEntity entity : regionEntities) {
            RegionCode regionCode = RegionCode.valueOf(entity.getRegion());
            ZoneId zone = ZoneId.of(entity.getIanaZone()); // validates IANA zone

            Set<DayOfWeek> weekendDays = parseWeekendDays(entity.getWeekendDays());
            Set<LocalDate> holidays = holidaysByRegion.getOrDefault(entity.getRegion(), Set.of());

            CutoffEntity cutoffEntity = cutoffsByRegion.get(entity.getRegion());
            if (cutoffEntity == null) {
                throw new IllegalStateException("No cutoff configured for region: " + entity.getRegion());
            }
            Cutoff cutoff = new Cutoff(cutoffEntity.getCutoffLocalTime());

            RegionCalendar cal = new RegionCalendar(regionCode, zone, weekendDays, holidays, cutoff);
            calendars.put(regionCode, cal);

            log.info("Loaded calendar for region {} (zone={}, holidays={}, cutoff={})",
                    regionCode, zone, holidays.size(), cutoff.localTime());
        }

        calendarRegistry().initialize(calendars);
        log.info("Calendar registry loaded successfully with {} regions.", calendars.size());
    }

    private Set<DayOfWeek> parseWeekendDays(String weekendDays) {
        return Arrays.stream(weekendDays.split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }
}
