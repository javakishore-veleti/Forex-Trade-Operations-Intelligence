package com.fxtradeops.eod.mcp;

import com.fxtradeops.domain.mcp.GatedTool;
import com.fxtradeops.domain.mcp.ToolEnvelope;
import com.fxtradeops.domain.mcp.ToolRisk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for EOD processing queries.
 * Exposed to the n8n supervisor agent via Spring AI MCP Server.
 */
@Component
public class EodTools {

    /**
     * Get the close status for a specific region.
     *
     * @param regionCode the ISO region code
     * @return ToolEnvelope containing regional close status
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getRegionalCloseStatus(String regionCode) {
        // TODO: Wire to ReadinessService when MCP server runtime is configured
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                regionCode,
                Map.of("region", regionCode, "closeStatus", "PLACEHOLDER", "blockers", List.of()),
                List.of("VIEW_READINESS_MAP", "VIEW_BLOCKERS")
        );
    }

    /**
     * Get the readiness status map across all regions.
     *
     * @return ToolEnvelope containing the global readiness status map
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getReadinessStatusMap() {
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                "GLOBAL",
                Map.of("regions", Map.of(), "overallReady", false),
                List.of("VIEW_REGIONAL_STATUS", "INITIATE_CLOSE")
        );
    }
}
