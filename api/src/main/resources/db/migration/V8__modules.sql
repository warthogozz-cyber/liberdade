-- Optional modules a family has switched on. No row means off; switching off keeps the row, so the audit trail
-- and updated_at still tell when and by whom.
CREATE TABLE workspace_modules (
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    module_key   text        NOT NULL CHECK (module_key IN ('planning', 'indicators', 'gamification', 'ventures')),
    enabled      boolean     NOT NULL,
    updated_by   uuid        NULL REFERENCES users (id) ON DELETE SET NULL,
    updated_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, module_key)
);
