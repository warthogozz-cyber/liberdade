-- Markets ("Feira", plan 10.1): the public OpenStreetMap extract, the family's own market rows and the family address.

-- Global (no workspace_id), like indicator_series: public OpenStreetMap data.
CREATE TABLE osm_market_imports (
    id            bigserial   PRIMARY KEY,
    osm_timestamp timestamptz NOT NULL,       -- the date of the data in OpenStreetMap (manifest)
    row_count     int         NOT NULL,
    sha256        text        NOT NULL,
    imported_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE osm_markets (
    osm_type      char(1)       NOT NULL CHECK (osm_type IN ('n', 'w', 'r')),
    osm_id        bigint        NOT NULL,
    kind          text          NOT NULL CHECK (kind IN ('SUPERMARKET', 'CONVENIENCE', 'GREENGROCER', 'MARKETPLACE')),
    name          text          NULL,
    brand         text          NULL,
    lat           numeric(8,5)  NOT NULL,
    lon           numeric(8,5)  NOT NULL,
    website       text          NULL,
    instagram     text          NULL,         -- from contact:instagram
    opening_hours text          NULL,
    street        text          NULL,
    housenumber   text          NULL,
    suburb        text          NULL,
    city          text          NULL,
    PRIMARY KEY (osm_type, osm_id)
);
CREATE INDEX osm_markets_by_point ON osm_markets (lat, lon);

-- The family's.
CREATE TABLE markets (
    id                uuid         PRIMARY KEY DEFAULT uuidv7(),
    workspace_id      uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    origin            text         NOT NULL CHECK (origin IN ('OSM', 'FAMILY')),
    osm_type          char(1)      NULL,
    osm_id            bigint       NULL,      -- no FK: the market can disappear from OpenStreetMap
    name              text         NULL,      -- only in FAMILY
    kind              text         NULL CHECK (kind IS NULL OR kind IN ('SUPERMARKET', 'CONVENIENCE', 'GREENGROCER', 'MARKETPLACE')),
    lat               numeric(8,5) NULL,      -- only in FAMILY; may be missing (created from a receipt)
    lon               numeric(8,5) NULL,
    cnpj              char(14)     NULL,      -- of the issuer on the receipts
    website           text         NULL,      -- the family's, on top of OpenStreetMap
    website_set_at    timestamptz  NULL,
    instagram         text         NULL,
    instagram_set_at  timestamptz  NULL,
    flyer_url         text         NULL,
    flyer_source      text         NULL,      -- up to 60 characters: "site do mercado", "WhatsApp"
    flyer_valid_until date         NULL,
    flyer_set_at      timestamptz  NULL,
    created_by        uuid         NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        uuid         NULL,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    archived_at       timestamptz  NULL,
    UNIQUE (workspace_id, id),
    CHECK ((origin = 'OSM' AND osm_type IS NOT NULL AND osm_id IS NOT NULL AND name IS NULL AND lat IS NULL)
        OR (origin = 'FAMILY' AND osm_type IS NULL AND osm_id IS NULL AND name IS NOT NULL)),
    FOREIGN KEY (workspace_id, created_by)
        REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by),
    FOREIGN KEY (workspace_id, updated_by)
        REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (updated_by)
);
CREATE UNIQUE INDEX markets_osm_once ON markets (workspace_id, osm_type, osm_id) WHERE origin = 'OSM';
CREATE UNIQUE INDEX markets_cnpj_once ON markets (workspace_id, cnpj) WHERE cnpj IS NOT NULL;

CREATE TABLE market_home (
    workspace_id uuid         PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    lat          numeric(6,3) NOT NULL,       -- 3 decimals, about 110 m
    lon          numeric(6,3) NOT NULL,
    label        text         NULL,           -- up to 40 characters, chosen by the person ("Casa")
    updated_by   uuid         NULL,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, updated_by)
        REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (updated_by)
);

-- The markets module can be switched on.
ALTER TABLE workspace_modules DROP CONSTRAINT workspace_modules_module_key_check;
ALTER TABLE workspace_modules ADD CONSTRAINT workspace_modules_module_key_check
    CHECK (module_key IN ('planning', 'indicators', 'gamification', 'ventures', 'markets'));

ALTER TABLE audit_events DROP CONSTRAINT audit_events_target_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_target_type_check
    CHECK (target_type IN ('USER', 'MEMBERSHIP', 'INVITATION', 'WORKSPACE', 'MODULE', 'CONSENT', 'ORDER', 'PLAN',
                           'RECURRING_RULE', 'GOAL', 'ACCOUNT', 'VENTURE', 'MARKET'));
