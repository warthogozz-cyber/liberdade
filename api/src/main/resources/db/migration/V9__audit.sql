-- The audit trail (LGPD): who did a sensitive action, to what, and when. It never keeps an address, a value, a
-- description or a name: only the action, its target and a few fixed labels in metadata. Deleting a family or an
-- account keeps the row and clears the link, so the trail outlives both until the nightly job removes it.
CREATE TABLE audit_events (
    id            uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id  uuid        NULL REFERENCES workspaces (id) ON DELETE SET NULL,
    actor_user_id uuid        NULL REFERENCES users (id) ON DELETE SET NULL,
    action        text        NOT NULL,
    target_type   text        NOT NULL CHECK (target_type IN ('USER', 'MEMBERSHIP', 'INVITATION', 'WORKSPACE', 'MODULE', 'CONSENT')),
    target_id     uuid        NULL,
    metadata      jsonb       NOT NULL DEFAULT '{}',
    occurred_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX audit_events_actor_ix ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX audit_events_target_ix ON audit_events (target_type, target_id, occurred_at DESC);
CREATE INDEX audit_events_occurred_ix ON audit_events (occurred_at);
