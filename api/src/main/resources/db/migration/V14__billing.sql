-- Billing: the orders a family placed, the plan it holds and the gateway events already handled. A paid order outlives
-- the family and the person (accounting): deleting either clears the link and keeps the amount and the dates.
CREATE TABLE billing_orders (
    id                  uuid        PRIMARY KEY DEFAULT uuidv7(),
    workspace_id        uuid        NULL REFERENCES workspaces (id) ON DELETE SET NULL,
    requested_by        uuid        NULL REFERENCES users (id) ON DELETE SET NULL,
    product             text        NOT NULL CHECK (product IN ('FOUNDER_2026')),
    amount_cents        bigint      NOT NULL CHECK (amount_cents > 0),
    currency            char(3)     NOT NULL DEFAULT 'BRL',
    status              text        NOT NULL CHECK (status IN
        ('CREATED', 'PAID', 'EXPIRED', 'CANCELED', 'REFUND_PENDING', 'REFUNDED', 'CHARGEBACK')),
    asaas_checkout_id   text        UNIQUE,
    asaas_payment_id    text        UNIQUE,
    paid_at             timestamptz NULL,
    refund_requested_at timestamptz NULL,
    refunded_at         timestamptz NULL,
    refund_reason       text        NULL CHECK (refund_reason IN ('CUSTOMER', 'DUPLICATE')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX billing_orders_workspace_ix ON billing_orders (workspace_id, created_at DESC);
CREATE INDEX billing_orders_status_ix ON billing_orders (status, created_at);
-- A family has at most one order waiting for payment.
CREATE UNIQUE INDEX billing_orders_one_open_ux ON billing_orders (workspace_id) WHERE status = 'CREATED';

CREATE TABLE workspace_plans (
    workspace_id    uuid        PRIMARY KEY REFERENCES workspaces (id) ON DELETE CASCADE,
    plan_key        text        NOT NULL CHECK (plan_key IN ('FAMILY')),
    founder         boolean     NOT NULL,
    source_order_id uuid        NULL REFERENCES billing_orders (id) ON DELETE SET NULL,
    starts_at       timestamptz NOT NULL,
    ends_at         timestamptz NOT NULL,
    theme           text        NULL,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Only what idempotency and diagnosis need: never the body of the event.
CREATE TABLE billing_webhook_events (
    event_id     text        PRIMARY KEY,
    event_type   text        NOT NULL,
    checkout_id  text        NULL,
    payment_id   text        NULL,
    received_at  timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz NULL,
    outcome      text        NULL
);

ALTER TABLE audit_events DROP CONSTRAINT audit_events_target_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_target_type_check
    CHECK (target_type IN ('USER', 'MEMBERSHIP', 'INVITATION', 'WORKSPACE', 'MODULE', 'CONSENT', 'ORDER', 'PLAN'));
