-- A family: the space where its people keep their money together.
CREATE TABLE workspaces (
    id                    uuid        PRIMARY KEY DEFAULT uuidv7(),
    name                  text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    base_currency         char(3)     NOT NULL DEFAULT 'BRL',
    timezone              text        NOT NULL DEFAULT 'America/Fortaleza',
    deletion_requested_at timestamptz NULL,
    version               bigint      NOT NULL DEFAULT 0,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

-- Who belongs to a family, and with which role. Removed members keep their row, with status REMOVED.
CREATE TABLE memberships (
    id                  uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id        uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id             uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                text        NOT NULL CHECK (role IN ('OWNER', 'ADULT', 'YOUTH', 'VIEWER')),
    nickname            text        NULL,
    status              text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    parental_consent_at timestamptz NULL,
    parental_consent_by uuid        NULL REFERENCES users (id),
    version             bigint      NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, user_id),
    -- Target of the composite foreign keys from the family's own tables.
    UNIQUE (workspace_id, id)
);

-- One family per person for now (D14). Two taps on "Começar" at once end here. Goes away with invitations.
CREATE UNIQUE INDEX memberships_one_active_family ON memberships (user_id) WHERE status = 'ACTIVE';
