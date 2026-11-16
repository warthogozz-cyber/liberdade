-- A person may now belong to several families (invitations), so the one-active-family rule goes away.
DROP INDEX memberships_one_active_family;

-- The lookups by person: their families, and whether they are the sole owner of one.
CREATE INDEX memberships_active_by_user ON memberships (user_id) WHERE status = 'ACTIVE';
