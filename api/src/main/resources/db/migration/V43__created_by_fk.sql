-- Authorship of the Feira's rows ("Feira", plan 10.3 follow-up): created_by is a membership id, like the other tables,
-- and becomes null when that membership goes away. V41 and V42 left it without the key.

-- The shopping list once stored the user id. Where that user has a membership in the family, use its id; any other
-- value (the member is gone, or it never matched) becomes null, so the constraints apply to data already there.
UPDATE shopping_list_items i
SET created_by = m.id
FROM memberships m
WHERE m.workspace_id = i.workspace_id AND m.user_id = i.created_by
  AND NOT EXISTS (SELECT 1 FROM memberships x WHERE x.workspace_id = i.workspace_id AND x.id = i.created_by);

UPDATE shopping_list_items i SET created_by = NULL
WHERE created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.workspace_id = i.workspace_id AND m.id = i.created_by);

UPDATE receipts r SET created_by = NULL
WHERE created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.workspace_id = r.workspace_id AND m.id = r.created_by);

UPDATE price_records p SET created_by = NULL
WHERE created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.workspace_id = p.workspace_id AND m.id = p.created_by);

ALTER TABLE shopping_list_items
    ADD FOREIGN KEY (workspace_id, created_by) REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by);
ALTER TABLE receipts
    ADD FOREIGN KEY (workspace_id, created_by) REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by);
ALTER TABLE price_records
    ADD FOREIGN KEY (workspace_id, created_by) REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by);
