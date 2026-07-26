package com.fxtradeops.riskcalc.mcp;

import com.fxtradeops.domain.mcp.GatedTool;
import com.fxtradeops.domain.mcp.ToolEnvelope;
import com.fxtradeops.domain.mcp.ToolRisk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for risk calculation queries.
 * Exposed to the n8n supervisor agent via Spring AI MCP Server.
 */
@Component
public class RiskTools {

    /**
     * Retrieve the risk result for a specific trade.
     *
     * @param tradeId the synthetic FX-prefixed trade identifier
     * @return ToolEnvelope containing risk computation result
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getRiskResult(String tradeId) {
        // TODO: Wire to RiskCalculationService when MCP server runtime is configured
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                tradeId,
                Map.of("tradeId", tradeId, "riskLevel", "PLACEHOLDER", "score", 0.0),
                List.of("VIEW_RULE_TRACE", "VIEW_AGGREGATION")
        );
    }

    /**
     * Retrieve the Drools rule trace for a risk computation.
     *
     * @param tradeId the synthetic FX-prefixed trade identifier
     * @return ToolEnvelope containing rule execution trace
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope getRuleTrace(String tradeId) {
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                tradeId,
                Map.of("tradeId", tradeId, "rulesEvaluated", List.of(), "fallbackApplied", false),
                List.of("VIEW_RISK_RESULT")
        );
    }
}
