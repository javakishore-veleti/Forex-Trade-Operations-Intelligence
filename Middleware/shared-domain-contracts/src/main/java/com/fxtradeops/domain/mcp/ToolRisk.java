package com.fxtradeops.domain.mcp;

/**
 * Risk classification for gated MCP tools.
 * <ul>
 *   <li>L — Low risk: read-only, no side effects</li>
 *   <li>M — Medium risk: may trigger downstream processing</li>
 *   <li>H — High risk: mutates state or triggers external actions</li>
 * </ul>
 */
public enum ToolRisk {
    L,
    M,
    H
}
