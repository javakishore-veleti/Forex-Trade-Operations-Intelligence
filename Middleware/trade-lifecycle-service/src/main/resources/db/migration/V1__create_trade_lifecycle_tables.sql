CREATE TABLE trade_current_state (
    trade_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
    status         VARCHAR(32)  NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE processed_events (
    event_id    VARCHAR(128) NOT NULL PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
