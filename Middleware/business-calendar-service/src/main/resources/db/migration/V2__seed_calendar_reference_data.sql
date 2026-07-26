-- V2: Seed reference data for Business Calendar
-- All holiday names are fictional. All time zones are standard IANA references.
-- Identifiers use synthetic FX- prefix where applicable.

-- Region calendars
INSERT INTO region_calendar (region, iana_zone, weekend_days, version) VALUES
    ('APAC',     'Asia/Singapore',   'SATURDAY,SUNDAY', 0),
    ('EMEA',     'Europe/London',    'SATURDAY,SUNDAY', 0),
    ('AMERICAS', 'America/New_York', 'SATURDAY,SUNDAY', 0),
    ('GLOBAL',   'America/New_York', 'SATURDAY,SUNDAY', 0);

-- Cutoffs (local time per region)
INSERT INTO cutoff (region, cutoff_local_time) VALUES
    ('APAC',     '17:00:00'),
    ('EMEA',     '17:00:00'),
    ('AMERICAS', '17:00:00'),
    ('GLOBAL',   '17:00:00');

-- Fictional holidays for APAC (2025-2026)
INSERT INTO holiday (region, holiday_date, name) VALUES
    ('APAC', '2025-01-01', 'FX-New-Dawn Day'),
    ('APAC', '2025-02-10', 'FX-Spring Festival'),
    ('APAC', '2025-05-01', 'FX-Labour Unity Day'),
    ('APAC', '2025-08-09', 'FX-Independence Celebration'),
    ('APAC', '2025-12-25', 'FX-Year End Harmony Day'),
    ('APAC', '2026-01-01', 'FX-New-Dawn Day'),
    ('APAC', '2026-02-09', 'FX-Spring Festival'),
    ('APAC', '2026-05-01', 'FX-Labour Unity Day'),
    ('APAC', '2026-08-09', 'FX-Independence Celebration'),
    ('APAC', '2026-12-25', 'FX-Year End Harmony Day');

-- Fictional holidays for EMEA (2025-2026)
INSERT INTO holiday (region, holiday_date, name) VALUES
    ('EMEA', '2025-01-01', 'FX-New Year Unity'),
    ('EMEA', '2025-04-18', 'FX-Spring Remembrance'),
    ('EMEA', '2025-04-21', 'FX-Renewal Monday'),
    ('EMEA', '2025-05-05', 'FX-May Accord Day'),
    ('EMEA', '2025-08-25', 'FX-Summer Rest Day'),
    ('EMEA', '2025-12-25', 'FX-Solstice Day'),
    ('EMEA', '2025-12-26', 'FX-Goodwill Day'),
    ('EMEA', '2026-01-01', 'FX-New Year Unity'),
    ('EMEA', '2026-04-03', 'FX-Spring Remembrance'),
    ('EMEA', '2026-04-06', 'FX-Renewal Monday'),
    ('EMEA', '2026-05-04', 'FX-May Accord Day'),
    ('EMEA', '2026-08-31', 'FX-Summer Rest Day'),
    ('EMEA', '2026-12-25', 'FX-Solstice Day'),
    ('EMEA', '2026-12-28', 'FX-Goodwill Day');

-- Fictional holidays for AMERICAS (2025-2026)
INSERT INTO holiday (region, holiday_date, name) VALUES
    ('AMERICAS', '2025-01-01', 'FX-New Epoch Day'),
    ('AMERICAS', '2025-01-20', 'FX-Leadership Day'),
    ('AMERICAS', '2025-02-17', 'FX-Heritage Day'),
    ('AMERICAS', '2025-05-26', 'FX-Remembrance Day'),
    ('AMERICAS', '2025-07-04', 'FX-Freedom Day'),
    ('AMERICAS', '2025-09-01', 'FX-Workers Accord Day'),
    ('AMERICAS', '2025-11-27', 'FX-Gratitude Day'),
    ('AMERICAS', '2025-12-25', 'FX-Solstice Celebration'),
    ('AMERICAS', '2026-01-01', 'FX-New Epoch Day'),
    ('AMERICAS', '2026-01-19', 'FX-Leadership Day'),
    ('AMERICAS', '2026-02-16', 'FX-Heritage Day'),
    ('AMERICAS', '2026-05-25', 'FX-Remembrance Day'),
    ('AMERICAS', '2026-07-03', 'FX-Freedom Day'),
    ('AMERICAS', '2026-09-07', 'FX-Workers Accord Day'),
    ('AMERICAS', '2026-11-26', 'FX-Gratitude Day'),
    ('AMERICAS', '2026-12-25', 'FX-Solstice Celebration');

-- Fictional holidays for GLOBAL (same as AMERICAS, base-country)
INSERT INTO holiday (region, holiday_date, name) VALUES
    ('GLOBAL', '2025-01-01', 'FX-Global New Epoch Day'),
    ('GLOBAL', '2025-01-20', 'FX-Global Leadership Day'),
    ('GLOBAL', '2025-05-26', 'FX-Global Remembrance Day'),
    ('GLOBAL', '2025-07-04', 'FX-Global Freedom Day'),
    ('GLOBAL', '2025-09-01', 'FX-Global Workers Accord Day'),
    ('GLOBAL', '2025-11-27', 'FX-Global Gratitude Day'),
    ('GLOBAL', '2025-12-25', 'FX-Global Solstice Celebration'),
    ('GLOBAL', '2026-01-01', 'FX-Global New Epoch Day'),
    ('GLOBAL', '2026-01-19', 'FX-Global Leadership Day'),
    ('GLOBAL', '2026-05-25', 'FX-Global Remembrance Day'),
    ('GLOBAL', '2026-07-03', 'FX-Global Freedom Day'),
    ('GLOBAL', '2026-09-07', 'FX-Global Workers Accord Day'),
    ('GLOBAL', '2026-11-26', 'FX-Global Gratitude Day'),
    ('GLOBAL', '2026-12-25', 'FX-Global Solstice Celebration');
