package com.fxtradeops.tradelifecycle.mcp;

import com.fxtradeops.domain.mcp.GatedTool;
import com.fxtradeops.domain.mcp.ToolEnvelope;
import com.fxtradeops.domain.mcp.ToolRisk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for trade lifecycle queries.
 * Exposed to the n8n supervisor agent via Spring AI MCP Server.
 */
@Component
public class GetTradeTools {

    /**
     * Retrieve the current state of a trade by its identifier.
     *
     * @param tradeId the synthetic FX-prefixed trade identifier
     * @return ToolEnvelope containing trade facts
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getTrade(String tradeId) {
        // TODO: Wire to LifecycleService when MCP server runtime is configured
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                tradeId,
                Map.of("tradeId", tradeId, "status", "PLACEHOLDER"),
                List.of("VIEW_TRADE", "VIEW_TIMELINE")
        );
    }

    /**
     * Retrieve the event history for a trade.
     *
     * @param tradeId the synthetic FX-prefixed trade identifier
     * @return ToolEnvelope containing trade events
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getTradeEvents(String tradeId) {
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                tradeId,
                Map.of("tradeId", tradeId, "events", List.of()),
                List.of("VIEW_TRADE")
        );
    }

    /**
     * Retrieve the full timeline of a trade lifecycle.
     *
     * @param tradeId the synthetic FX-prefixed trade identifier
     * @return ToolEnvelope containing timeline entries
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getTradeTimeline(String tradeId) {
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                tradeId,
                Map.of("tradeId", tradeId, "timeline", List.of()),
                List.of("VIEW_TRADE", "VIEW_EVENTS")
        );
    }
}
