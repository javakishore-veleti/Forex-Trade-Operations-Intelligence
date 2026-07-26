package com.fxtradeops.calendar.mcp;

import com.fxtradeops.domain.mcp.GatedTool;
import com.fxtradeops.domain.mcp.ToolEnvelope;
import com.fxtradeops.domain.mcp.ToolRisk;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for business calendar queries.
 * Exposed to the n8n supervisor agent via Spring AI MCP Server.
 */
@Component
public class CalendarTools {

    /**
     * Retrieve the business calendar for a given region.
     *
     * @param regionCode the ISO region code
     * @return ToolEnvelope containing calendar data
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getBusinessCalendar(String regionCode) {
        // TODO: Wire to CalendarQueryService when MCP server runtime is configured
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                regionCode,
                Map.of("region", regionCode, "holidays", List.of(), "cutoffs", List.of()),
                List.of("CLASSIFY_BOOKING_DATE", "VIEW_CUTOFFS")
        );
    }

    /**
     * Classify a specific date as a valid booking date for the given region.
     *
     * @param regionCode the ISO region code
     * @param date       the date to classify
     * @return ToolEnvelope containing booking date classification
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope classifyBookingDate(String regionCode, LocalDate date) {
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                regionCode,
                Map.of("region", regionCode, "date", date.toString(),
                        "isBusinessDay", true, "reason", "PLACEHOLDER"),
                List.of("VIEW_CALENDAR", "COMPUTE_NEXT_BUSINESS_DAY")
        );
    }
}
