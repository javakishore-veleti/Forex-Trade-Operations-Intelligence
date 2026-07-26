package com.fxtradeops.calendar.api;

import com.fxtradeops.calendar.application.CalendarQueryService;
import com.fxtradeops.calendar.config.SecurityConfig;
import com.fxtradeops.calendar.domain.BookingDate;
import com.fxtradeops.calendar.domain.BusinessDayReason;
import com.fxtradeops.calendar.domain.GlobalBusinessDate;
import com.fxtradeops.calendar.domain.Holiday;
import com.fxtradeops.calendar.domain.UnknownRegionCalendarException;
import com.fxtradeops.calendar.web.CorrelationIdFilter;
import com.fxtradeops.calendar.web.GlobalExceptionHandler;
import com.fxtradeops.domain.reference.RegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests (MockMvc): each endpoint success + 400 (invalid region) + 404 (unknown calendar).
 * (GP-Rq-12.1)
 */
@WebMvcTest(CalendarQueryController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, GlobalExceptionHandler.class})
class CalendarQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalendarQueryService queryService;

    @Test
    @DisplayName("GET business-day → 200 with correct body")
    void businessDay_success() throws Exception {
        when(queryService.classifyBusinessDay(RegionCode.APAC, LocalDate.of(2025, 1, 6)))
                .thenReturn(BusinessDayReason.BUSINESS_DAY);

        mockMvc.perform(get("/api/v1/calendars/APAC/business-day")
                        .param("date", "2025-01-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("APAC"))
                .andExpect(jsonPath("$.date").value("2025-01-06"))
                .andExpect(jsonPath("$.businessDay").value(true))
                .andExpect(jsonPath("$.reason").value("BUSINESS_DAY"));
    }

    @Test
    @DisplayName("GET business-day with invalid region → 400")
    void businessDay_invalidRegion() throws Exception {
        mockMvc.perform(get("/api/v1/calendars/INVALID/business-day")
                        .param("date", "2025-01-06"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("GET business-day with unknown calendar → 404")
    void businessDay_unknownCalendar() throws Exception {
        when(queryService.classifyBusinessDay(eq(RegionCode.GLOBAL), any()))
                .thenThrow(new UnknownRegionCalendarException(RegionCode.GLOBAL));

        mockMvc.perform(get("/api/v1/calendars/GLOBAL/business-day")
                        .param("date", "2025-01-06"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errors[0].message", containsString("GLOBAL")));
    }

    @Test
    @DisplayName("GET add-business-days → 200")
    void addBusinessDays_success() throws Exception {
        when(queryService.addBusinessDays(RegionCode.EMEA, LocalDate.of(2025, 1, 3), 2))
                .thenReturn(LocalDate.of(2025, 1, 7));

        mockMvc.perform(get("/api/v1/calendars/EMEA/add-business-days")
                        .param("date", "2025-01-03")
                        .param("n", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("EMEA"))
                .andExpect(jsonPath("$.from").value("2025-01-03"))
                .andExpect(jsonPath("$.n").value(2))
                .andExpect(jsonPath("$.result").value("2025-01-07"));
    }

    @Test
    @DisplayName("GET business-days-between → 200")
    void businessDaysBetween_success() throws Exception {
        when(queryService.businessDaysBetween(RegionCode.AMERICAS,
                LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10)))
                .thenReturn(4L);

        mockMvc.perform(get("/api/v1/calendars/AMERICAS/business-days-between")
                        .param("from", "2025-01-06")
                        .param("to", "2025-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    @DisplayName("GET holidays → 200")
    void holidays_success() throws Exception {
        when(queryService.holidaysForYear(RegionCode.APAC, 2025))
                .thenReturn(List.of(
                        new Holiday(LocalDate.of(2025, 1, 1), "FX-New-Dawn Day"),
                        new Holiday(LocalDate.of(2025, 5, 1), "FX-Labour Unity Day")
                ));

        mockMvc.perform(get("/api/v1/calendars/APAC/holidays")
                        .param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("FX-New-Dawn Day"));
    }

    @Test
    @DisplayName("GET booking-date → 200")
    void bookingDate_success() throws Exception {
        Instant instant = Instant.parse("2025-01-06T09:00:00Z");
        when(queryService.bookingDate(RegionCode.APAC, instant))
                .thenReturn(new BookingDate(RegionCode.APAC, instant, LocalDate.of(2025, 1, 6)));

        mockMvc.perform(get("/api/v1/calendars/APAC/booking-date")
                        .param("instant", "2025-01-06T09:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingDate").value("2025-01-06"));
    }

    @Test
    @DisplayName("GET post-cutoff → 200")
    void postCutoff_success() throws Exception {
        Instant instant = Instant.parse("2025-01-06T10:00:00Z");
        when(queryService.isPostCutoff(RegionCode.EMEA, instant))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/calendars/EMEA/post-cutoff")
                        .param("instant", "2025-01-06T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCutoff").value(false));
    }

    @Test
    @DisplayName("GET global business-date → 200")
    void globalBusinessDate_success() throws Exception {
        Instant instant = Instant.parse("2025-01-06T15:00:00Z");
        when(queryService.globalBusinessDate(instant))
                .thenReturn(new GlobalBusinessDate(instant, LocalDate.of(2025, 1, 6),
                        ZoneId.of("America/New_York")));

        mockMvc.perform(get("/api/v1/calendars/global/business-date")
                        .param("instant", "2025-01-06T15:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalBusinessDate").value("2025-01-06"))
                .andExpect(jsonPath("$.anchorZone").value("America/New_York"));
    }

    @Test
    @DisplayName("Correlation ID is echoed in response")
    void correlationId_echoed() throws Exception {
        when(queryService.classifyBusinessDay(RegionCode.APAC, LocalDate.of(2025, 1, 6)))
                .thenReturn(BusinessDayReason.BUSINESS_DAY);

        mockMvc.perform(get("/api/v1/calendars/APAC/business-day")
                        .param("date", "2025-01-06")
                        .header("X-Correlation-Id", "FX-test-corr-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "FX-test-corr-123"));
    }

    @Test
    @DisplayName("Missing correlation ID generates a new one")
    void correlationId_generated() throws Exception {
        when(queryService.classifyBusinessDay(RegionCode.APAC, LocalDate.of(2025, 1, 6)))
                .thenReturn(BusinessDayReason.BUSINESS_DAY);

        mockMvc.perform(get("/api/v1/calendars/APAC/business-day")
                        .param("date", "2025-01-06"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
