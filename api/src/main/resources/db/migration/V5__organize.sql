-- Two active accounts of a family never share a name; an archived one gives its name back.
CREATE UNIQUE INDEX accounts_unique_name ON accounts (workspace_id, name) WHERE archived_at IS NULL;

-- Whether the person closed the "getting started" card.
ALTER TABLE memberships ADD COLUMN getting_started_dismissed_at timestamptz;

-- The palette a category may use; today no category has a color, so nothing breaks.
ALTER TABLE categories ADD CONSTRAINT categories_color_check
    CHECK (color IS NULL OR color IN ('TEAL', 'BLUE', 'INDIGO', 'PURPLE', 'PINK', 'RED', 'ORANGE', 'GREEN'));
