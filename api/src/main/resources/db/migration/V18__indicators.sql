-- Plan 8.1: the public market indicators (Banco Central series) and the health of each collection. Public data:
-- no table here belongs to a family, so none has a workspace_id.

CREATE TABLE indicator_series (
    code            text PRIMARY KEY,            -- SELIC_TARGET, CDI, IPCA_MONTH, IPCA_12M, USD_PTAX_SELL
    source          text NOT NULL CHECK (source IN ('BCB_SGS', 'BCB_PTAX')),
    source_ref      text NOT NULL,               -- 432, 4389, 433, 13522, CotacaoDolarPeriodo
    unit            text NOT NULL CHECK (unit IN ('PERCENT_YEAR', 'PERCENT_MONTH', 'PERCENT_12M', 'BRL')),
    name            text NOT NULL,
    last_success_at timestamptz NULL,            -- the last query that answered well, with a new point or not
    last_failure_at timestamptz NULL,
    failure_streak  int NOT NULL DEFAULT 0
);

CREATE TABLE indicator_points (
    series_code text NOT NULL REFERENCES indicator_series (code),
    ref_date    date NOT NULL,
    value       numeric(19,6) NOT NULL,
    fetched_at  timestamptz NOT NULL,
    source_url  text NOT NULL,
    PRIMARY KEY (series_code, ref_date)
);

INSERT INTO indicator_series (code, source, source_ref, unit, name) VALUES
    ('SELIC_TARGET', 'BCB_SGS', '432', 'PERCENT_YEAR', 'Selic (meta)'),
    ('CDI', 'BCB_SGS', '4389', 'PERCENT_YEAR', 'CDI'),
    ('IPCA_MONTH', 'BCB_SGS', '433', 'PERCENT_MONTH', 'IPCA do mês'),
    ('IPCA_12M', 'BCB_SGS', '13522', 'PERCENT_12M', 'IPCA em 12 meses'),
    ('USD_PTAX_SELL', 'BCB_PTAX', 'CotacaoDolarPeriodo', 'BRL', 'Dólar (PTAX venda)');
