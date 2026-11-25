ALTER TABLE transactions
    ADD COLUMN venture_id uuid NULL,
    ADD FOREIGN KEY (workspace_id, venture_id)
        REFERENCES ventures (workspace_id, id) ON DELETE SET NULL (venture_id),
    ADD CONSTRAINT transactions_venture_kind CHECK (venture_id IS NULL OR kind IN ('INCOME', 'EXPENSE'));
CREATE INDEX transactions_by_venture ON transactions (workspace_id, venture_id, occurred_on)
    WHERE venture_id IS NOT NULL;
