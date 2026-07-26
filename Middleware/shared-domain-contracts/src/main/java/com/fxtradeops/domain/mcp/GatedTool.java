package com.fxtradeops.domain.mcp;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an MCP tool method with its risk classification.
 * The n8n supervisor agent uses this to decide whether human-in-the-loop
 * approval is required before execution.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GatedTool {
    ToolRisk value() default ToolRisk.L;
}
