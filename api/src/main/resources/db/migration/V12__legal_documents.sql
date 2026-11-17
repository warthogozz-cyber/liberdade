-- TERMS and PRIVACY record the person's own acceptance of a document version, with no family: workspace_id is null for
-- them and set for PARENTAL, which always belongs to a family.
ALTER TABLE consents ALTER COLUMN workspace_id DROP NOT NULL;

ALTER TABLE consents
    ADD CONSTRAINT consents_workspace_by_purpose CHECK ((purpose = 'PARENTAL') = (workspace_id IS NOT NULL));

-- Still one open consent per person and family and purpose. NULLS NOT DISTINCT makes the family-less acceptances count
-- as one scope too, so a person never has two open acceptances of the same document.
DROP INDEX consents_one_open;
CREATE UNIQUE INDEX consents_one_open ON consents (user_id, workspace_id, purpose) NULLS NOT DISTINCT
    WHERE revoked_at IS NULL AND superseded_at IS NULL;

COMMENT ON TABLE consents IS 'What a person agreed to. PARENTAL: a guardian vouches for a YOUTH member, in a family. '
    'TERMS and PRIVACY: the person accepted a version of the document, with no family. A revoked consent keeps its row.';
