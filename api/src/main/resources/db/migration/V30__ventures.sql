-- Extra-income projects (plan 9.1): the project, its costs, its products and the steps to start.
CREATE TABLE ventures (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    type         text        NOT NULL CHECK (type IN ('FOOD', 'DIGITAL_PRODUCT', 'CONTENT', 'RESALE',
                                                      'SERVICE', 'OTHER')),
    template_key text        NULL,       -- the template it came from; null for a blank project
    status       text        NOT NULL DEFAULT 'PLANNING'
                             CHECK (status IN ('PLANNING', 'ACTIVE', 'PAUSED', 'CLOSED')),
    started_on   date        NULL,       -- the day it started selling; required outside PLANNING
    closed_on    date        NULL,       -- the day it was closed; only in CLOSED
    currency     char(3)     NOT NULL,   -- the family currency at creation
    created_by   uuid        NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,
    CHECK (status = 'PLANNING' OR started_on IS NOT NULL),
    CHECK ((status = 'CLOSED') = (closed_on IS NOT NULL)),
    CHECK (closed_on IS NULL OR closed_on >= started_on),
    FOREIGN KEY (workspace_id, created_by)
        REFERENCES memberships (workspace_id, id) ON DELETE SET NULL (created_by),
    UNIQUE (workspace_id, id)
);

CREATE TABLE venture_cost_items (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    venture_id   uuid          NOT NULL,
    kind         text          NOT NULL CHECK (kind IN ('INITIAL', 'FIXED_MONTHLY')),
    name         text          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    amount       numeric(19,4) NOT NULL CHECK (amount > 0),
    currency     char(3)       NOT NULL,
    is_example   boolean       NOT NULL DEFAULT false,  -- came from the template and was not edited yet
    position     smallint      NOT NULL,
    FOREIGN KEY (workspace_id, venture_id) REFERENCES ventures (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE venture_products (
    id                  uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id        uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    venture_id          uuid          NOT NULL,
    name                text          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    unit_variable_cost  numeric(19,4) NOT NULL CHECK (unit_variable_cost >= 0),
    unit_price          numeric(19,4) NOT NULL CHECK (unit_price > 0),
    planned_units_month integer       NOT NULL CHECK (planned_units_month BETWEEN 0 AND 1000000),
    currency            char(3)       NOT NULL,
    is_example          boolean       NOT NULL DEFAULT false,
    position            smallint      NOT NULL,
    FOREIGN KEY (workspace_id, venture_id) REFERENCES ventures (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE venture_steps (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    venture_id   uuid        NOT NULL,
    title        text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
    position     smallint    NOT NULL,
    done_at      timestamptz NULL,
    FOREIGN KEY (workspace_id, venture_id) REFERENCES ventures (workspace_id, id) ON DELETE CASCADE
);

CREATE INDEX ventures_by_workspace ON ventures (workspace_id, status);
CREATE INDEX venture_cost_items_by_venture ON venture_cost_items (workspace_id, venture_id);
CREATE INDEX venture_products_by_venture ON venture_products (workspace_id, venture_id);
CREATE INDEX venture_steps_by_venture ON venture_steps (workspace_id, venture_id);

ALTER TABLE audit_events DROP CONSTRAINT audit_events_target_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_target_type_check
    CHECK (target_type IN ('USER', 'MEMBERSHIP', 'INVITATION', 'WORKSPACE', 'MODULE', 'CONSENT', 'ORDER', 'PLAN',
                           'RECURRING_RULE', 'GOAL', 'ACCOUNT', 'VENTURE'));
