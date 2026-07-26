package com.fxtradeops.riskcalc.domain;

/**
 * Thrown when risk arithmetic invariants are violated (e.g., factor sum mismatch).
 */
public class RiskArithmeticException extends RuntimeException {

    public RiskArithmeticException(String message) {
        super(message);
    }

    public RiskArithmeticException(String message, Throwable cause) {
        super(message, cause);
    }
}
