package com.fxtradeops.tradeingest.domain;

import java.util.List;
import java.util.Map;

/**
 * Exception thrown when domain-level validation rules are violated.
 * Contains a collection of field-level errors.
 */
public class DomainValidationException extends RuntimeException {

    private final List<Map<String, String>> errors;

    public DomainValidationException(List<Map<String, String>> errors) {
        super("Domain validation failed");
        this.errors = errors;
    }

    public List<Map<String, String>> getErrors() {
        return errors;
    }
}
