ALTER TABLE rent_schedule
    DROP CONSTRAINT rent_schedule_status_check;

ALTER TABLE rent_schedule
    ADD CONSTRAINT rent_schedule_status_check
    CHECK (status IN ('PENDING_APPROVAL', 'SETUP_FAILED', 'ACTIVE', 'PAUSED', 'PAYMENT_FAILED', 'PAST_DUE'));
