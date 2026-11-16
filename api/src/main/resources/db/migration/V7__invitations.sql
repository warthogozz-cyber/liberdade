-- Single-use links that bring someone into a family (INVITE) or back into their own account (RECOVERY). Only the
-- SHA-256 of the token is stored; the token itself is shown once, when the link is made.
CREATE TABLE invitations (
    id                   uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id         uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    kind                 text        NOT NULL CHECK (kind IN ('INVITE', 'RECOVERY')),
    role                 text        NOT NULL CHECK (role IN ('OWNER', 'ADULT', 'YOUTH', 'VIEWER')),
    nickname             text        NULL CHECK (char_length(nickname) BETWEEN 1 AND 40),
    target_membership_id uuid        NULL,
    parental_consent_at  timestamptz NULL,
    parental_consent_by  uuid        NULL REFERENCES users (id) ON DELETE SET NULL,
    token_hash           bytea       NOT NULL UNIQUE,
    created_by           uuid        NULL REFERENCES users (id) ON DELETE SET NULL,
    expires_at           timestamptz NOT NULL,
    used_at              timestamptz NULL,
    revoked_at           timestamptz NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    CHECK ((kind = 'INVITE') = (nickname IS NOT NULL)),
    CHECK ((kind = 'RECOVERY') = (target_membership_id IS NOT NULL)),
    FOREIGN KEY (workspace_id, target_membership_id)
        REFERENCES memberships (workspace_id, id) ON DELETE CASCADE
);

CREATE INDEX invitations_workspace_expiry_ix ON invitations (workspace_id, expires_at);

-- When an owner's recovery link last brought the person back in (Task 4).
ALTER TABLE memberships ADD COLUMN recovered_at timestamptz;
