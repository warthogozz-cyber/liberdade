-- Recurring rules: the mold of a transaction that repeats, and the dates it falls on. The rules never delete rows,
-- they are archived; a rule's planned transactions live in `transactions`, kept out of every total.
CREATE TABLE recurring_rules (
    id             uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id   uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    kind           text          NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount         numeric(19,4) NOT NULL CHECK (amount > 0),
    currency       char(3)       NOT NULL,
    account_id     uuid          NOT NULL,
    to_account_id  uuid          NULL,
    category_id    uuid          NULL,
    payment_method text          NULL
        CHECK (payment_method IN ('PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO', 'BANK_TRANSFER', 'CASH')),
    description    text          NULL CHECK (char_length(description) BETWEEN 1 AND 140),
    frequency      text          NOT NULL CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    interval       smallint      NOT NULL CHECK (interval BETWEEN 1 AND 12),
    start_on       date          NOT NULL,
    end_on         date          NULL CHECK (end_on >= start_on),
    next_due_on    date          NULL,
    created_by     uuid          NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    archived_at    timestamptz   NULL,
    version        bigint        NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, account_id) REFERENCES accounts (workspace_id, id),
    FOREIGN KEY (workspace_id, to_account_id) REFERENCES accounts (workspace_id, id),
    FOREIGN KEY (workspace_id, category_id) REFERENCES categories (workspace_id, id),
    FOREIGN KEY (workspace_id, created_by) REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by),
    CHECK ((kind = 'TRANSFER') = (to_account_id IS NOT NULL)),
    CHECK ((kind = 'TRANSFER') = (category_id IS NULL)),
    CHECK (to_account_id <> account_id),
    CHECK (kind <> 'TRANSFER' OR payment_method IS NULL)
);

-- A planned transaction is a row of `transactions` that no sum counts. Confirming it makes it a CONFIRMED one.
-- Deleting a rule for good (the app only archives) keeps the confirmed history and drops the link.
ALTER TABLE transactions
    ADD COLUMN status text NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'PLANNED')),
    ADD COLUMN recurring_rule_id uuid NULL,
    ADD COLUMN scheduled_on date NULL,
    ADD FOREIGN KEY (workspace_id, recurring_rule_id)
        REFERENCES recurring_rules (workspace_id, id) ON DELETE SET NULL (recurring_rule_id),
    ADD CONSTRAINT transactions_rule_has_scheduled_on CHECK (recurring_rule_id IS NULL OR scheduled_on IS NOT NULL);

-- One transaction for each occurrence of a rule, even a skipped (deleted) one: the generator never makes it again.
CREATE UNIQUE INDEX transactions_one_per_occurrence
    ON transactions (workspace_id, recurring_rule_id, scheduled_on) WHERE recurring_rule_id IS NOT NULL;

-- Planning is on by default (D15), for the families that exist and, in the app, for the new ones.
INSERT INTO workspace_modules (workspace_id, module_key, enabled)
SELECT id, 'planning', true FROM workspaces
ON CONFLICT DO NOTHING;

ALTER TABLE audit_events DROP CONSTRAINT audit_events_target_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_target_type_check
    CHECK (target_type IN ('USER', 'MEMBERSHIP', 'INVITATION', 'WORKSPACE', 'MODULE', 'CONSENT', 'ORDER', 'PLAN',
                           'RECURRING_RULE'));
