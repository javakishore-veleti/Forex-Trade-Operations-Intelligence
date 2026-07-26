-- V1__create_risk_tables.sql
-- Flyway migration for risk-calculation-service schema

CREATE TABLE IF NOT EXISTS risk_results (
    calculation_id VARCHAR(36) PRIMARY KEY,
    trade_id VARCHAR(10) NOT NULL,
    correlation_id VARCHAR(36),
    risk_amount NUMERIC(19,4) NOT NULL,
    risk_currency CHAR(3) NOT NULL,
    region_code VARCHAR(8),
    trading_book_id VARCHAR(50),
    calculated_at TIMESTAMPTZ NOT NULL,
    rule_version VARCHAR(20),
    risk_level VARCHAR(8) NOT NULL,
    rules_fired TEXT,
    contributing_factors TEXT,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_risk_results_trade_id ON risk_results(trade_id);
CREATE INDEX idx_risk_results_region ON risk_results(region_code);

CREATE TABLE IF NOT EXISTS risk_aggregations (
    scope_type VARCHAR(8) NOT NULL,
    scope_id VARCHAR(50) NOT NULL,
    total_risk_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    risk_currency CHAR(3) NOT NULL,
    trade_count INT NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT DEFAULT 0,
    PRIMARY KEY (scope_type, scope_id)
);

CREATE TABLE IF NOT EXISTS risk_limits (
    limit_id VARCHAR(36) PRIMARY KEY,
    scope_type VARCHAR(12) NOT NULL,
    scope_id VARCHAR(50) NOT NULL,
    limit_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL
);

CREATE INDEX idx_risk_limits_scope ON risk_limits(scope_type, scope_id);

CREATE TABLE IF NOT EXISTS limit_breaches (
    breach_id VARCHAR(36) PRIMARY KEY,
    calculation_id VARCHAR(36) NOT NULL,
    scope_type VARCHAR(12) NOT NULL,
    scope_id VARCHAR(50) NOT NULL,
    limit_amount NUMERIC(19,4) NOT NULL,
    observed_amount NUMERIC(19,4) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_limit_breaches_scope ON limit_breaches(scope_type, scope_id);
CREATE INDEX idx_limit_breaches_calc ON limit_breaches(calculation_id);

CREATE TABLE IF NOT EXISTS eod_risk_snapshots (
    snapshot_id VARCHAR(36) PRIMARY KEY,
    scope_type VARCHAR(8) NOT NULL,
    scope_id VARCHAR(50) NOT NULL,
    business_date DATE NOT NULL,
    total_risk_amount NUMERIC(19,4) NOT NULL,
    trade_count INT NOT NULL,
    rule_version VARCHAR(20),
    snapshotted_at TIMESTAMPTZ NOT NULL,
    UNIQUE(scope_type, scope_id, business_date)
);
