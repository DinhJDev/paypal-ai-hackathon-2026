CREATE TABLE lease_workspace (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES owner_access(id) ON DELETE CASCADE,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    tenant_name varchar(120) NOT NULL DEFAULT '',
    suite_label varchar(120) NOT NULL DEFAULT '',
    start_date date,
    end_date date,
    amount numeric(10,2) CHECK (amount > 0),
    frequency varchar(16) CHECK (frequency IN ('WEEKLY','MONTHLY')),
    first_due_date date,
    late_fee_terms varchar(2000) NOT NULL DEFAULT '',
    furnished_items varchar(4000) NOT NULL DEFAULT '',
    updated_at timestamptz NOT NULL DEFAULT now(),
    confirmed_at timestamptz,
    confirmed_by uuid,
    schedule_id uuid UNIQUE REFERENCES rent_schedule(id),
    CONSTRAINT lease_confirmation_complete CHECK (
        (confirmed_at IS NULL AND confirmed_by IS NULL AND schedule_id IS NULL) OR
        (confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL AND schedule_id IS NOT NULL)
    )
);
CREATE INDEX lease_workspace_owner_updated_idx ON lease_workspace(owner_id,updated_at DESC);
