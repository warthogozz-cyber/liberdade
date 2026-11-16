-- Deleting an account (D23, D24) and a family (D25). Both wait out a 30-day grace first.
-- A person's memberships outlive their account: the row stays REMOVED with no user, so the family's transactions
-- keep their author's nickname. Deleting the family removes the rows through the workspace cascade instead.
ALTER TABLE memberships ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE memberships
    DROP CONSTRAINT memberships_user_id_fkey,
    ADD CONSTRAINT memberships_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

-- The guardian's account going away only clears who vouched, like the same column of invitations.
ALTER TABLE memberships
    DROP CONSTRAINT memberships_parental_consent_by_fkey,
    ADD CONSTRAINT memberships_parental_consent_by_fkey
        FOREIGN KEY (parental_consent_by) REFERENCES users (id) ON DELETE SET NULL;

-- What the nightly purge jobs look for.
CREATE INDEX users_deletion_requested_ix ON users (deletion_requested_at) WHERE deletion_requested_at IS NOT NULL;
CREATE INDEX workspaces_deletion_requested_ix ON workspaces (deletion_requested_at)
    WHERE deletion_requested_at IS NOT NULL;
