-- Where a family keeps its money. The balance is computed from the transactions, never stored.
CREATE TABLE accounts (
    id                  uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id        uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name                text          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    type                text          NOT NULL
                                      CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH', 'CREDIT_CARD', 'WALLET', 'INVESTMENT')),
    currency            char(3)       NOT NULL,
    initial_balance     numeric(19,4) NOT NULL DEFAULT 0,
    -- The account holder, when it has one.
    owner_membership_id uuid          NULL,
    archived_at         timestamptz   NULL,
    version             bigint        NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, owner_membership_id) REFERENCES memberships (workspace_id, id),
    UNIQUE (workspace_id, id)
);

-- What a transaction was for. One level of subcategories, through parent_id.
CREATE TABLE categories (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    parent_id    uuid        NULL,
    kind         text        NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    icon         text        NULL,
    color        text        NULL,
    is_default   boolean     NOT NULL DEFAULT false,
    sort_order   int         NOT NULL DEFAULT 0,
    archived_at  timestamptz NULL,
    version      bigint      NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, parent_id) REFERENCES categories (workspace_id, id),
    UNIQUE (workspace_id, id)
);

-- "Outros" exists under both kinds; NULLS NOT DISTINCT makes two top-level categories with one name clash (D30).
CREATE UNIQUE INDEX categories_unique_name ON categories (workspace_id, kind, parent_id, name) NULLS NOT DISTINCT
    WHERE archived_at IS NULL;

-- Money in, out, or between accounts. The id comes from the device (UUIDv7), so a resend finds the same row.
CREATE TABLE transactions (
    id            uuid          PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    kind          text          NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount        numeric(19,4) NOT NULL CHECK (amount > 0),
    currency      char(3)       NOT NULL,
    occurred_on   date          NOT NULL,
    account_id    uuid          NOT NULL,
    to_account_id uuid          NULL,
    category_id   uuid          NULL,
    description   text          NULL CHECK (char_length(description) BETWEEN 1 AND 140),
    -- Who spent or received it, and who wrote it down.
    member_id     uuid          NOT NULL,
    created_by    uuid          NOT NULL,
    deleted_at    timestamptz   NULL,
    version       bigint        NOT NULL DEFAULT 0,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, account_id) REFERENCES accounts (workspace_id, id),
    FOREIGN KEY (workspace_id, to_account_id) REFERENCES accounts (workspace_id, id),
    FOREIGN KEY (workspace_id, category_id) REFERENCES categories (workspace_id, id),
    FOREIGN KEY (workspace_id, member_id) REFERENCES memberships (workspace_id, id),
    FOREIGN KEY (workspace_id, created_by) REFERENCES memberships (workspace_id, id),
    -- A transfer is one row: it has a destination account and no category; nothing else has either.
    CHECK ((kind = 'TRANSFER') = (to_account_id IS NOT NULL)),
    CHECK ((kind = 'TRANSFER') = (category_id IS NULL)),
    CHECK (to_account_id <> account_id)
);

CREATE INDEX transactions_by_date ON transactions (workspace_id, occurred_on);
CREATE INDEX transactions_by_account ON transactions (workspace_id, account_id, occurred_on);
CREATE INDEX transactions_by_category ON transactions (workspace_id, category_id, occurred_on);
