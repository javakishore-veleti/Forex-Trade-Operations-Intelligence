-- V1__create_captured_trades.sql
-- Creates the captured_trades table, trade_id_seq sequence, and idempotency_keys table.

CREATE SEQUENCE trade_id_seq START 1 INCREMENT 1;

CREATE TABLE captured_trades (
    id              BIGSERIAL       PRIMARY KEY,
    trade_id        VARCHAR(10)     NOT NULL UNIQUE,
    correlation_id  VARCHAR(36)     NOT NULL,
    currency_pair_code VARCHAR(7)   NOT NULL,
    base_currency   VARCHAR(3)      NOT NULL,
    quote_currency  VARCHAR(3)      NOT NULL,
    notional_amount NUMERIC(19,4)   NOT NULL,
    notional_currency VARCHAR(3)    NOT NULL,
    direction       VARCHAR(4)      NOT NULL,
    trade_date      DATE            NOT NULL,
    value_date      DATE            NOT NULL,
    counterparty_id VARCHAR(50)     NOT NULL,
    trading_book_id VARCHAR(50)     NOT NULL,
    region_code     VARCHAR(8)      NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'CAPTURED',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(36)     PRIMARY KEY,
    trade_id        VARCHAR(10)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
