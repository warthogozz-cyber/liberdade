-- What a person agreed to, and who agreed for them. Today only PARENTAL is written: a guardian vouches for a YOUTH
-- member. TERMS and PRIVACY are reserved for the sign-up consent of a later plan. A revoked consent keeps its row.
-- Deleting the family or the youth's account deletes the row; deleting the guardian's account only clears granted_by.
CREATE TABLE consents (
    id               uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id          uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    workspace_id     uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    purpose          text        NOT NULL CHECK (purpose IN ('TERMS', 'PRIVACY', 'PARENTAL')),
    document_version text        NOT NULL,
    granted_by       uuid        NULL REFERENCES users (id) ON DELETE SET NULL,
    granted_at       timestamptz NOT NULL DEFAULT now(),
    revoked_at       timestamptz NULL,
    -- Closed by a newer consent for the same stay (a re-join or a new promotion to YOUTH), not by the guardian.
    superseded_at    timestamptz NULL
);

-- One open consent per person and family and purpose: a newer one supersedes the older.
CREATE UNIQUE INDEX consents_one_open ON consents (user_id, workspace_id, purpose)
    WHERE revoked_at IS NULL AND superseded_at IS NULL;

CREATE INDEX consents_user_ix ON consents (user_id);
CREATE INDEX consents_granted_by_ix ON consents (granted_by);
