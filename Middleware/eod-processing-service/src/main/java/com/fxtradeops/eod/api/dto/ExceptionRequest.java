package com.fxtradeops.eod.api.dto;

/**
 * Request body for applying an exception to a blocker.
 */
public record ExceptionRequest(String blockerId, String approvalReference) {
}
