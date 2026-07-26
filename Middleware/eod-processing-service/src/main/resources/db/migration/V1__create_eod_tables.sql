-- V1__create_eod_tables.sql
-- EOD Processing Service schema

CREATE TABLE regional_close (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    region_code VARCHAR(8) NOT NULL,
    status VARCHAR(12),
    unmet_conditions TEXT,
    updated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(business_date, region_code)
);

CREATE TABLE branch_completion (
    id BIGSERIAL PRIMARY KEY,
    business_date DATE NOT NULL,
    region_code VARCHAR(8) NOT NULL,
    branch_id VARCHAR(50) NOT NULL,
    complete BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(business_date, region_code, branch_id)
);

CREATE TABLE blocker (
    blocker_id VARCHAR(36) PRIMARY KEY,
    business_date DATE NOT NULL,
    region_code VARCHAR(8) NOT NULL,
    blocker_type VARCHAR(24),
    reference VARCHAR(50),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    approval_reference VARCHAR(100),
    detected_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE consolidation (
    business_date DATE PRIMARY KEY,
    status VARCHAR(12),
    contributing_regions TEXT,
    applied_exceptions TEXT,
    consolidated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE eod_audit (
    audit_id VARCHAR(36) PRIMARY KEY,
    business_date DATE NOT NULL,
    region_code VARCHAR(8),
    action VARCHAR(24),
    approval_reference VARCHAR(100),
    detail TEXT,
    recorded_at TIMESTAMPTZ
);

CREATE TABLE processed_event (
    event_id VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMPTZ
);

-- Indexes for common queries
CREATE INDEX idx_regional_close_date ON regional_close(business_date);
CREATE INDEX idx_branch_completion_date_region ON branch_completion(business_date, region_code);
CREATE INDEX idx_blocker_date_region ON blocker(business_date, region_code);
CREATE INDEX idx_blocker_unresolved ON blocker(business_date, region_code, resolved);
