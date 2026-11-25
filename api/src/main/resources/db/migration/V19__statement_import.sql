-- Statement import (plan 8.2). The key is a SHA-256 of the statement line, so one line is never imported twice.
ALTER TABLE transactions ADD COLUMN import_key text NULL
    CHECK (import_key IS NULL OR import_key ~ '^[0-9a-f]{64}$');

-- Deleted rows count too: a transaction deleted but not yet purged still blocks the same line.
CREATE UNIQUE INDEX transactions_import_key
    ON transactions (workspace_id, import_key) WHERE import_key IS NOT NULL;

-- The key of a purged transaction, kept for 13 months so a deleted line does not come back. Only a hash.
CREATE TABLE import_tombstones (
    workspace_id uuid NOT NULL,
    account_id   uuid NOT NULL,
    import_key   text NOT NULL,
    purged_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, account_id, import_key),
    FOREIGN KEY (workspace_id, account_id) REFERENCES accounts (workspace_id, id) ON DELETE CASCADE
);
CREATE INDEX import_tombstones_by_key ON import_tombstones (workspace_id, import_key);
CREATE INDEX import_tombstones_by_age ON import_tombstones (purged_at);
