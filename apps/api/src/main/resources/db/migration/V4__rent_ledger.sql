ALTER TABLE rent_schedule DROP CONSTRAINT rent_schedule_status_check;
ALTER TABLE rent_schedule ADD CONSTRAINT rent_schedule_status_check
    CHECK (status IN ('SCHEDULED', 'PENDING_APPROVAL', 'SETUP_FAILED', 'ACTIVE', 'PAUSED', 'PAYMENT_FAILED', 'PAST_DUE'));

CREATE TABLE rent_period (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id uuid NOT NULL REFERENCES rent_schedule(id) ON DELETE CASCADE,
    due_date date NOT NULL,
    amount numeric(10,2) NOT NULL CHECK (amount > 0),
    collection_held boolean NOT NULL DEFAULT false,
    review_required boolean NOT NULL DEFAULT false,
    hold_reason varchar(32),
    reviewed_at timestamptz,
    reviewed_by uuid REFERENCES owner_access(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    revision bigint NOT NULL DEFAULT 0,
    UNIQUE (schedule_id, due_date)
);

-- Existing provider attempts remain intact and contribute to the new balances.
INSERT INTO rent_period (schedule_id, due_date, amount, collection_held, review_required, hold_reason)
SELECT schedule_id, due_date, amount, status IN ('FAILED','REFUNDED'), status IN ('FAILED','REFUNDED'),
       CASE WHEN status IN ('FAILED','REFUNDED') THEN 'PROVIDER_REVIEW' END
FROM rent_payment;
INSERT INTO rent_period (schedule_id, due_date, amount, collection_held, review_required, hold_reason)
SELECT id, next_due_date, amount, status IN ('PAST_DUE','PAYMENT_FAILED'), status IN ('PAST_DUE','PAYMENT_FAILED'),
       CASE WHEN status IN ('PAST_DUE','PAYMENT_FAILED') THEN 'PROVIDER_REVIEW' END
FROM rent_schedule ON CONFLICT (schedule_id, due_date) DO NOTHING;

CREATE TABLE rent_external_payment (
    id uuid PRIMARY KEY,
    period_id uuid NOT NULL REFERENCES rent_period(id) ON DELETE CASCADE,
    amount numeric(10,2) NOT NULL CHECK (amount > 0),
    paid_on date NOT NULL,
    method varchar(24) NOT NULL CHECK (method IN ('CASH','VENMO','BANK_TRANSFER','OTHER')),
    reference varchar(120) NOT NULL DEFAULT '',
    recorded_by uuid NOT NULL REFERENCES owner_access(id),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    reversed_at timestamptz,
    reversed_by uuid REFERENCES owner_access(id),
    reversal_reason varchar(240),
    CHECK ((reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL)
        OR (reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND length(trim(reversal_reason)) > 0))
);
CREATE INDEX rent_external_payment_period_idx ON rent_external_payment (period_id, recorded_at);
