package com.fxtradeops.reconciliation.mcp;

import com.fxtradeops.domain.mcp.GatedTool;
import com.fxtradeops.domain.mcp.ToolEnvelope;
import com.fxtradeops.domain.mcp.ToolRisk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for state reconciliation operations.
 * Exposed to the n8n supervisor agent via Spring AI MCP Server.
 */
@Component
public class ReconciliationTools {

    /**
     * Evaluate the canonical state for a given trade.
     *
     * @param tradeId the synthetic FX-prefixed trade identifier
     * @return ToolEnvelope containing canonical state evaluation
     */
    @GatedTool(ToolRisk.L)
    public ToolEnvelope evaluateCanonicalState(String tradeId) {
        // TODO: Wire to ReconciliationService when MCP server runtime is configured
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                tradeId,
                Map.of("tradeId", tradeId, "canonicalState", "PLACEHOLDER", "sourcesEvaluated", 0),
                List.of("VIEW_DIVERGENCES", "START_RECONCILIATION")
        );
    }

    /**
     * Start a reconciliation sweep for a specific trade or scope.
     *
     * @param tradeId the synthetic FX-prefixed trade identifier
     * @return ToolEnvelope containing reconciliation initiation result
     */
    @GatedTool(ToolRisk.M)
    public ToolEnvelope startReconciliation(String tradeId) {
        return ToolEnvelope.success(
                UUID.randomUUID().toString(),
                tradeId,
                Map.of("tradeId", tradeId, "reconciliationId", UUID.randomUUID().toString(), "status", "INITIATED"),
                List.of("VIEW_RESULT", "CANCEL_RECONCILIATION")
        );
    }
}
