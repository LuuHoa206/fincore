CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    preferred_currency CHAR(3) NOT NULL DEFAULT 'VND',
    time_zone VARCHAR(60) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    name VARCHAR(100) NOT NULL,
    wallet_type VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL,
    current_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wallets_type CHECK (wallet_type IN ('CASH', 'BANK', 'E_WALLET', 'CREDIT', 'SAVINGS')),
    CONSTRAINT uk_wallets_user_name UNIQUE (user_id, name)
);

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    category_type VARCHAR(20) NOT NULL,
    icon VARCHAR(50),
    color VARCHAR(20),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_categories_type CHECK (category_type IN ('INCOME', 'EXPENSE'))
);

CREATE UNIQUE INDEX uk_categories_owner_name_type
    ON categories (COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid), name, category_type);

CREATE TABLE money_jars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    name VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL,
    allocated_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    spending_limit NUMERIC(19,4),
    color VARCHAR(20),
    icon VARCHAR(50),
    allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_money_jars_user_name UNIQUE (user_id, name),
    CONSTRAINT ck_money_jars_spending_limit CHECK (spending_limit IS NULL OR spending_limit >= 0)
);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    category_id UUID REFERENCES categories(id),
    transaction_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    posted_at TIMESTAMPTZ,
    reversed_transaction_id UUID REFERENCES financial_transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_transactions_type CHECK (transaction_type IN (
        'INCOME', 'EXPENSE', 'TRANSFER', 'JAR_TRANSFER', 'REFUND', 'ADJUSTMENT', 'REVERSAL'
    )),
    CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'POSTED', 'REVERSED', 'FAILED')),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES financial_transactions(id),
    wallet_id UUID REFERENCES wallets(id),
    account_kind VARCHAR(20) NOT NULL,
    account_label VARCHAR(120),
    signed_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ledger_entries_kind CHECK (account_kind IN ('WALLET', 'EXTERNAL')),
    CONSTRAINT ck_ledger_entries_amount CHECK (signed_amount <> 0),
    CONSTRAINT ck_ledger_entries_account CHECK (
        (account_kind = 'WALLET' AND wallet_id IS NOT NULL)
        OR (account_kind = 'EXTERNAL' AND wallet_id IS NULL AND account_label IS NOT NULL)
    )
);

CREATE TABLE jar_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jar_id UUID NOT NULL REFERENCES money_jars(id),
    transaction_id UUID REFERENCES financial_transactions(id),
    signed_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    reason VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_jar_movements_amount CHECK (signed_amount <> 0)
);

CREATE TABLE allocation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_allocation_rules_user_name UNIQUE (user_id, name)
);

CREATE TABLE allocation_rule_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL REFERENCES allocation_rules(id) ON DELETE CASCADE,
    jar_id UUID NOT NULL REFERENCES money_jars(id),
    percentage NUMERIC(5,2) NOT NULL,
    CONSTRAINT uk_allocation_rule_items_rule_jar UNIQUE (rule_id, jar_id),
    CONSTRAINT ck_allocation_rule_items_percentage CHECK (percentage > 0 AND percentage <= 100)
);

CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    period_start DATE NOT NULL,
    limit_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    warning_threshold SMALLINT NOT NULL DEFAULT 80,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_budgets_user_category_period UNIQUE (user_id, category_id, period_start),
    CONSTRAINT ck_budgets_limit CHECK (limit_amount > 0),
    CONSTRAINT ck_budgets_threshold CHECK (warning_threshold BETWEEN 1 AND 100)
);

CREATE TABLE saving_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    jar_id UUID NOT NULL REFERENCES money_jars(id),
    name VARCHAR(120) NOT NULL,
    target_amount NUMERIC(19,4) NOT NULL,
    target_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_saving_goals_amount CHECK (target_amount > 0),
    CONSTRAINT ck_saving_goals_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'PAUSED', 'CANCELLED'))
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idempotency_key)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES app_users(id),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    correlation_id VARCHAR(100),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_wallets_user ON wallets(user_id);
CREATE INDEX idx_money_jars_user ON money_jars(user_id);
CREATE INDEX idx_transactions_user_occurred ON financial_transactions(user_id, occurred_at DESC);
CREATE INDEX idx_transactions_category ON financial_transactions(category_id);
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_wallet ON ledger_entries(wallet_id);
CREATE INDEX idx_jar_movements_jar_created ON jar_movements(jar_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs(actor_user_id, created_at DESC);
CREATE INDEX idx_outbox_events_unpublished ON outbox_events(occurred_at) WHERE published_at IS NULL;
