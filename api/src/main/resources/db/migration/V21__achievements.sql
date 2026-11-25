-- What each membership has earned. The row follows the membership, so it goes with the family on a cascade; a removed
-- member keeps the membership row, so a listener deletes the achievements then.
CREATE TABLE achievements (
    workspace_id    uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    membership_id   uuid NOT NULL,
    achievement_key text NOT NULL,
    earned_at       timestamptz NOT NULL DEFAULT now(),
    silent          boolean NOT NULL DEFAULT false,
    PRIMARY KEY (membership_id, achievement_key),
    FOREIGN KEY (workspace_id, membership_id) REFERENCES memberships (workspace_id, id) ON DELETE CASCADE
);

-- The weeks with entries are read by who wrote them down.
CREATE INDEX transactions_by_creator ON transactions (workspace_id, created_by, created_at);
