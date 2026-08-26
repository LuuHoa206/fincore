ALTER TABLE allocation_rules
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'VND';

ALTER TABLE allocation_rules
    ALTER COLUMN currency DROP DEFAULT;

CREATE UNIQUE INDEX uk_allocation_rules_active_user_currency
    ON allocation_rules (user_id, currency)
    WHERE enabled = TRUE;
