-- ---------------------------------------------------------------------------
-- Reference data.
--
-- Static by nature, so it ships as a migration. Market rates and trades are
-- seeded at startup instead (see DemoDataSeeder): they are generated relative
-- to today's date, so the demo still has a live-looking blotter in six months.
--
-- The holiday calendar is a working subset, not an exhaustive one. A real
-- installation loads a full calendar per currency from a vendor feed; the
-- point here is that value dates are derived from a calendar at all, rather
-- than from "today plus two".
-- ---------------------------------------------------------------------------

insert into currency (code, name, minor_units)
values ('USD', 'United States Dollar', 2),
       ('EUR', 'Euro', 2),
       ('GBP', 'Pound Sterling', 2),
       ('JPY', 'Japanese Yen', 0),
       ('CHF', 'Swiss Franc', 2),
       ('NGN', 'Nigerian Naira', 2),
       ('ZAR', 'South African Rand', 2);

insert into currency_pair (symbol, base_ccy, quote_ccy, spot_lag_days, pip_factor, rate_scale)
values ('EURUSD', 'EUR', 'USD', 2, 10000, 5),
       ('GBPUSD', 'GBP', 'USD', 2, 10000, 5),
       ('USDJPY', 'USD', 'JPY', 2, 100, 3),
       ('USDCHF', 'USD', 'CHF', 2, 10000, 5),
       ('EURGBP', 'EUR', 'GBP', 2, 10000, 5),
       ('USDNGN', 'USD', 'NGN', 2, 100, 4),
       ('USDZAR', 'USD', 'ZAR', 2, 10000, 5);

insert into counterparty (code, name, country, trade_limit)
values ('MERIDIAN', 'Meridian Bank plc', 'GB', 25000000.00),
       ('KESTREL', 'Kestrel Capital Markets', 'US', 10000000.00),
       ('NORTHGT', 'Northgate Treasury Services', 'CH', 15000000.00),
       ('ATLASCB', 'Atlas Commercial Bank', 'NG', 5000000.00),
       ('HARMTTN', 'Harmattan Investment Bank', 'NG', 7500000.00),
       ('DELISTED', 'Sable Financial (delisted)', 'ZA', 1000000.00);

-- Sable is inactive on purpose: booking against it is the clearest way to
-- demonstrate that validation rejects trades rather than just storing them.
update counterparty set active = false where code = 'DELISTED';

insert into holiday (currency_code, holiday_date, description)
values
    -- USD
    ('USD', date '2026-11-26', 'Thanksgiving Day'),
    ('USD', date '2026-12-25', 'Christmas Day'),
    ('USD', date '2027-01-01', 'New Year''s Day'),
    ('USD', date '2027-01-18', 'Martin Luther King Jr. Day'),
    ('USD', date '2027-02-15', 'Presidents'' Day'),
    ('USD', date '2027-05-31', 'Memorial Day'),
    ('USD', date '2027-07-05', 'Independence Day (observed)'),
    ('USD', date '2027-09-06', 'Labor Day'),
    ('USD', date '2027-11-25', 'Thanksgiving Day'),
    ('USD', date '2027-12-24', 'Christmas Day (observed)'),

    -- EUR (TARGET2)
    ('EUR', date '2026-12-25', 'Christmas Day'),
    ('EUR', date '2027-01-01', 'New Year''s Day'),
    ('EUR', date '2027-03-26', 'Good Friday'),
    ('EUR', date '2027-03-29', 'Easter Monday'),
    ('EUR', date '2027-12-24', 'Christmas Eve (TARGET closed)'),

    -- GBP
    ('GBP', date '2026-12-25', 'Christmas Day'),
    ('GBP', date '2026-12-28', 'Boxing Day (substitute)'),
    ('GBP', date '2027-01-01', 'New Year''s Day'),
    ('GBP', date '2027-03-26', 'Good Friday'),
    ('GBP', date '2027-03-29', 'Easter Monday'),
    ('GBP', date '2027-05-03', 'Early May Bank Holiday'),
    ('GBP', date '2027-05-31', 'Spring Bank Holiday'),
    ('GBP', date '2027-08-30', 'Summer Bank Holiday'),
    ('GBP', date '2027-12-24', 'Christmas Day (substitute)'),

    -- NGN
    ('NGN', date '2026-10-01', 'Independence Day'),
    ('NGN', date '2026-12-25', 'Christmas Day'),
    ('NGN', date '2026-12-28', 'Boxing Day (observed)'),
    ('NGN', date '2027-01-01', 'New Year''s Day'),
    ('NGN', date '2027-05-03', 'Workers'' Day (observed)'),
    ('NGN', date '2027-06-14', 'Democracy Day (observed)'),
    ('NGN', date '2027-10-01', 'Independence Day'),

    -- JPY
    ('JPY', date '2026-09-21', 'Respect for the Aged Day'),
    ('JPY', date '2026-11-03', 'Culture Day'),
    ('JPY', date '2026-11-23', 'Labour Thanksgiving Day'),
    ('JPY', date '2027-01-01', 'New Year''s Day'),
    ('JPY', date '2027-01-11', 'Coming of Age Day'),
    ('JPY', date '2027-02-11', 'National Foundation Day'),
    ('JPY', date '2027-05-03', 'Constitution Memorial Day'),
    ('JPY', date '2027-05-04', 'Greenery Day'),
    ('JPY', date '2027-05-05', 'Children''s Day'),

    -- CHF
    ('CHF', date '2026-12-25', 'Christmas Day'),
    ('CHF', date '2027-01-01', 'New Year''s Day'),
    ('CHF', date '2027-03-26', 'Good Friday'),
    ('CHF', date '2027-03-29', 'Easter Monday'),
    ('CHF', date '2027-08-02', 'Swiss National Day (observed)'),

    -- ZAR
    ('ZAR', date '2026-12-25', 'Christmas Day'),
    ('ZAR', date '2027-01-01', 'New Year''s Day'),
    ('ZAR', date '2027-03-26', 'Good Friday'),
    ('ZAR', date '2027-04-27', 'Freedom Day');
