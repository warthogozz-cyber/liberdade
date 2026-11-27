-- Shopping list and price comparison ("Feira", plan 10.3): the family's one list, and the "not the same product" answers.

CREATE TABLE shopping_list_items (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    workspace_id uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    product_id   uuid          NOT NULL,
    quantity     numeric(19,4) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    checked_at   timestamptz   NULL,
    position     int           NOT NULL,
    created_by   uuid          NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, product_id),
    FOREIGN KEY (workspace_id, product_id) REFERENCES products (workspace_id, id) ON DELETE CASCADE
);

-- "These two are not the same product": do not suggest the join again. product_a is the smaller of the two ids.
CREATE TABLE product_join_dismissals (
    workspace_id uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    product_a    uuid NOT NULL,
    product_b    uuid NOT NULL,
    PRIMARY KEY (workspace_id, product_a, product_b),
    CHECK (product_a < product_b),
    FOREIGN KEY (workspace_id, product_a) REFERENCES products (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, product_b) REFERENCES products (workspace_id, id) ON DELETE CASCADE
);

ALTER TABLE audit_events DROP CONSTRAINT audit_events_target_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_target_type_check
    CHECK (target_type IN ('USER', 'MEMBERSHIP', 'INVITATION', 'WORKSPACE', 'MODULE', 'CONSENT', 'ORDER', 'PLAN',
                           'RECURRING_RULE', 'GOAL', 'ACCOUNT', 'VENTURE', 'MARKET', 'RECEIPT', 'PRODUCT'));
