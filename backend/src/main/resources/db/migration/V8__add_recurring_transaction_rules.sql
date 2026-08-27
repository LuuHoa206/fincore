CREATE TABLE recurring_transaction_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    name VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes TEXT,
    frequency VARCHAR(20) NOT NULL,
    schedule_day SMALLINT NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    auto_record BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    apply_allocation_rule BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_recurring_rules_type CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_recurring_rules_amount CHECK (amount > 0),
    CONSTRAINT ck_recurring_rules_frequency CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_recurring_rules_schedule_day CHECK (schedule_day BETWEEN 1 AND 31)
);

CREATE INDEX idx_recurring_rules_due
    ON recurring_transaction_rules(next_run_at)
    WHERE enabled = TRUE AND auto_record = TRUE;

CREATE INDEX idx_recurring_rules_user_next_run
    ON recurring_transaction_rules(user_id, next_run_at);
