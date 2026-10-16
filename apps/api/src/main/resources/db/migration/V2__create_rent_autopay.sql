CREATE TABLE rent_schedule (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id uuid NOT NULL REFERENCES owner_access(id) ON DELETE CASCADE,
    tenant_label varchar(120) NOT NULL CHECK (length(trim(tenant_label)) > 0),
    amount numeric(10, 2) NOT NULL CHECK (amount > 0),
    frequency varchar(16) NOT NULL CHECK (frequency IN ('WEEKLY', 'MONTHLY')),
    next_due_date date NOT NULL,
    monthly_anchor_day integer NOT NULL CHECK (monthly_anchor_day BETWEEN 1 AND 31),
    status varchar(24) NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'SETUP_FAILED', 'ACTIVE', 'PAUSED', 'PAYMENT_FAILED')),
    paypal_setup_token_id varchar(64) UNIQUE,
    paypal_payment_token_id varchar(255),
    approval_url text
);

CREATE INDEX rent_schedule_owner_due_idx ON rent_schedule (owner_id, next_due_date);
CREATE INDEX rent_schedule_due_active_idx ON rent_schedule (next_due_date) WHERE status = 'ACTIVE';

CREATE TABLE rent_payment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id uuid NOT NULL REFERENCES rent_schedule(id) ON DELETE CASCADE,
    due_date date NOT NULL,
    amount numeric(10, 2) NOT NULL CHECK (amount > 0),
    currency_code char(3) NOT NULL DEFAULT 'USD' CHECK (currency_code = 'USD'),
    status varchar(16) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    idempotency_key varchar(108) NOT NULL UNIQUE,
    paypal_order_id varchar(64) UNIQUE,
    paypal_capture_id varchar(64) UNIQUE,
    failure_code varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rent_payment_schedule_due_unique UNIQUE (schedule_id, due_date)
);

CREATE INDEX rent_payment_schedule_created_idx ON rent_payment (schedule_id, created_at DESC);

CREATE TABLE paypal_webhook_event (
    id varchar(128) PRIMARY KEY,
    event_type varchar(128) NOT NULL,
    resource_id varchar(128),
    received_at timestamptz NOT NULL DEFAULT now()
);
