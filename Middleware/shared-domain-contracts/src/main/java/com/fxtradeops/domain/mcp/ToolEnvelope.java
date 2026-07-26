package com.fxtradeops.domain.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standardised envelope returned by every MCP tool invocation.
 * Carries structured facts, violations, permitted actions, and provenance metadata.
 *
 * @param requestId          Correlation ID linking this response to the agent request
 * @param businessEntity     The primary business entity identifier (e.g., trade ID, region code)
 * @param status             Outcome status of the tool call
 * @param facts              Key-value map of factual data returned by the tool
 * @param violations         List of rule or invariant violations detected
 * @param permittedActions   Actions the agent is allowed to take given current state
 * @param evidence           Supporting evidence or audit trail entries
 * @param dataClassification Data sensitivity classification (e.g., INTERNAL, CONFIDENTIAL)
 * @param expiresAt          Instant after which this envelope should not be cached
 */
public record ToolEnvelope(
        String requestId,
        String businessEntity,
        ToolStatus status,
        Map<String, Object> facts,
        List<String> violations,
        List<String> permittedActions,
        List<String> evidence,
        String dataClassification,
        Instant expiresAt
) {
    /**
     * Factory for a successful envelope with no violations.
     */
    public static ToolEnvelope success(String requestId, String businessEntity,
                                       Map<String, Object> facts, List<String> permittedActions) {
        return new ToolEnvelope(
                requestId, businessEntity, ToolStatus.SUCCESS,
                facts, List.of(), permittedActions, List.of(),
                "INTERNAL", Instant.now().plusSeconds(300)
        );
    }

    /**
     * Factory for a failure envelope.
     */
    public static ToolEnvelope failure(String requestId, String businessEntity,
                                       List<String> violations, List<String> evidence) {
        return new ToolEnvelope(
                requestId, businessEntity, ToolStatus.FAILURE,
                Map.of(), violations, List.of(), evidence,
                "INTERNAL", Instant.now().plusSeconds(60)
        );
    }
}
