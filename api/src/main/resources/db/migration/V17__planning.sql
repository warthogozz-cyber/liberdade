-- Plan 7.2: monthly limits per expense category, family goals and their contributions, and the planning flags of
-- the ledger. Every new table is scoped to the family through composite foreign keys (ADR-0008).

-- A monthly spending limit for one expense category. One row per category and month; the month is its first day.
CREATE TABLE budgets (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    category_id  uuid          NOT NULL,
    month        date          NOT NULL CHECK (extract(day FROM month) = 1),
    limit_amount numeric(19,4) NOT NULL CHECK (limit_amount > 0),
    currency     char(3)       NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, category_id) REFERENCES categories (workspace_id, id),
    UNIQUE (workspace_id, category_id, month)
);

-- Something the family saves for. Its progress is the linked account's balance, or the sum of its contributions.
CREATE TABLE goals (
    id                uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id      uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name              text          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    target_amount     numeric(19,4) NOT NULL CHECK (target_amount > 0),
    currency          char(3)       NOT NULL,
    -- The month it is due, as its first day.
    due_on            date          NULL CHECK (extract(day FROM due_on) = 1),
    icon              text          NOT NULL,
    linked_account_id uuid          NULL,
    achieved_at       timestamptz   NULL,
    archived_at       timestamptz   NULL,
    created_by        uuid          NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    version           bigint        NOT NULL DEFAULT 0,
    FOREIGN KEY (workspace_id, linked_account_id)
        REFERENCES accounts (workspace_id, id) ON DELETE SET NULL (linked_account_id),
    FOREIGN KEY (workspace_id, created_by)
        REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by),
    UNIQUE (workspace_id, id)
);

-- Money put into (positive) or taken out of (negative) a goal without a linked account.
CREATE TABLE goal_contributions (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    goal_id      uuid          NOT NULL,
    amount       numeric(19,4) NOT NULL CHECK (amount <> 0),
    occurred_on  date          NOT NULL,
    member_id    uuid          NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, goal_id) REFERENCES goals (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, member_id) REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (member_id)
);

CREATE INDEX goal_contributions_by_goal ON goal_contributions (workspace_id, goal_id, occurred_on);

-- The accounts that make up the emergency reserve, and the expense categories that are fixed costs.
ALTER TABLE accounts ADD COLUMN is_reserve boolean NOT NULL DEFAULT false;
ALTER TABLE categories ADD COLUMN is_fixed boolean NOT NULL DEFAULT false;

-- How many months of costs the reserve should cover, and the monthly cost the family types in when it has
-- too little history for the app to work it out.
ALTER TABLE workspaces
    ADD COLUMN reserve_target_months smallint NOT NULL DEFAULT 6 CHECK (reserve_target_months BETWEEN 1 AND 24),
    ADD COLUMN manual_monthly_cost numeric(19,4) NULL CHECK (manual_monthly_cost > 0);

ALTER TABLE audit_events DROP CONSTRAINT audit_events_target_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_target_type_check
    CHECK (target_type IN ('USER', 'MEMBERSHIP', 'INVITATION', 'WORKSPACE', 'MODULE', 'CONSENT', 'ORDER', 'PLAN',
                           'RECURRING_RULE', 'GOAL'));
