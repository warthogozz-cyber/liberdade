-- The family's country and language, and how a transaction was paid. Existing families are Brazilian.
ALTER TABLE workspaces
    ADD COLUMN country char(2) NOT NULL DEFAULT 'BR' CHECK (country IN ('BR')),
    ADD COLUMN locale  text    NOT NULL DEFAULT 'pt-BR' CHECK (locale IN ('pt-BR'));

-- A transfer moves money between the family's own accounts, so it has no payment method.
ALTER TABLE transactions
    ADD COLUMN payment_method text NULL
        CHECK (payment_method IN ('PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO', 'BANK_TRANSFER', 'CASH')),
    ADD CONSTRAINT transactions_transfer_without_payment_method CHECK (kind <> 'TRANSFER' OR payment_method IS NULL);

-- The composite key that the tag links point at.
ALTER TABLE transactions ADD CONSTRAINT transactions_workspace_id_id_key UNIQUE (workspace_id, id);

-- The family's tags. A name is free again once its tag is archived: there is no way back, typing it creates a new tag.
CREATE TABLE tags (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 30),
    created_by   uuid        NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    archived_at  timestamptz NULL,
    UNIQUE (workspace_id, id),
    FOREIGN KEY (workspace_id, created_by) REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by)
);

CREATE UNIQUE INDEX tags_active_name ON tags (workspace_id, lower(name)) WHERE archived_at IS NULL;

CREATE TABLE transaction_tags (
    workspace_id   uuid NOT NULL,
    transaction_id uuid NOT NULL,
    tag_id         uuid NOT NULL,
    PRIMARY KEY (workspace_id, transaction_id, tag_id),
    FOREIGN KEY (workspace_id, transaction_id) REFERENCES transactions (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, tag_id) REFERENCES tags (workspace_id, id) ON DELETE CASCADE
);

CREATE INDEX transaction_tags_by_tag ON transaction_tags (workspace_id, tag_id);
