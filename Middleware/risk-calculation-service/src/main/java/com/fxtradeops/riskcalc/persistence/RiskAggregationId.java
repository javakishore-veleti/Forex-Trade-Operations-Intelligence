package com.fxtradeops.riskcalc.persistence;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for risk_aggregations.
 */
public class RiskAggregationId implements Serializable {

    private String scopeType;
    private String scopeId;

    public RiskAggregationId() {
    }

    public RiskAggregationId(String scopeType, String scopeId) {
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiskAggregationId that = (RiskAggregationId) o;
        return Objects.equals(scopeType, that.scopeType) && Objects.equals(scopeId, that.scopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopeType, scopeId);
    }
}
