-- V1: Create Business Calendar reference-data schema
-- All holidays are fictional; all time zones are standard IANA references.

CREATE TABLE region_calendar (
    region       VARCHAR(20)  NOT NULL PRIMARY KEY,
    iana_zone    VARCHAR(50)  NOT NULL,
    weekend_days VARCHAR(100) NOT NULL DEFAULT 'SATURDAY,SUNDAY',
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE holiday (
    id           BIGSERIAL    NOT NULL PRIMARY KEY,
    region       VARCHAR(20)  NOT NULL REFERENCES region_calendar(region),
    holiday_date DATE         NOT NULL,
    name         VARCHAR(200) NOT NULL,
    CONSTRAINT uq_holiday_region_date UNIQUE (region, holiday_date)
);

CREATE INDEX idx_holiday_region_date ON holiday (region, holiday_date);

CREATE TABLE cutoff (
    region           VARCHAR(20) NOT NULL PRIMARY KEY REFERENCES region_calendar(region),
    cutoff_local_time TIME        NOT NULL
);
