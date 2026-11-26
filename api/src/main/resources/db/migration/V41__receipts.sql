-- Receipts and prices ("Feira", plan 10.2): products, aliases, NFC-e receipts (only the key hash) and price records.

CREATE TABLE products (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    gtin         text        NULL,                -- 8, 12, 13 or 14 digits, with the check digit
    unit         text        NOT NULL DEFAULT 'UN' CHECK (unit IN ('UN', 'KG', 'G', 'L', 'ML', 'CX', 'PCT', 'DZ')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, id)
);
CREATE UNIQUE INDEX products_gtin_once ON products (workspace_id, gtin) WHERE gtin IS NOT NULL;

-- "This description, at this market, is this product."
CREATE TABLE product_aliases (
    workspace_id     uuid NOT NULL,
    market_id        uuid NOT NULL,
    description_norm text NOT NULL,
    product_id       uuid NOT NULL,
    PRIMARY KEY (workspace_id, market_id, description_norm),
    FOREIGN KEY (workspace_id, market_id) REFERENCES markets (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, product_id) REFERENCES products (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE receipts (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    market_id    uuid        NOT NULL,
    key_hash     text        NOT NULL,            -- SHA-256 of the access key; the key itself is never stored
    issued_on    date        NOT NULL,
    mode         text        NOT NULL CHECK (mode IN ('FETCHED', 'MANUAL')),
    item_count   int         NOT NULL CHECK (item_count BETWEEN 1 AND 200),
    created_by   uuid        NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, key_hash),
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, market_id) REFERENCES markets (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE price_records (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    market_id    uuid          NOT NULL,
    product_id   uuid          NOT NULL,
    receipt_id   uuid          NULL,
    observed_on  date          NOT NULL,
    unit         text          NOT NULL CHECK (unit IN ('UN', 'KG', 'G', 'L', 'ML', 'CX', 'PCT', 'DZ')),
    quantity     numeric(19,4) NULL CHECK (quantity IS NULL OR quantity > 0),
    unit_price   numeric(19,4) NOT NULL CHECK (unit_price > 0),
    created_by   uuid          NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, market_id) REFERENCES markets (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, product_id) REFERENCES products (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, receipt_id) REFERENCES receipts (workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX price_records_latest ON price_records (workspace_id, product_id, market_id, observed_on DESC);
