CREATE TABLE split_bills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    expense_transaction_id UUID NOT NULL UNIQUE REFERENCES financial_transactions(id),
    name VARCHAR(120) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    payer_share_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_split_bills_amount CHECK (total_amount > 0),
    CONSTRAINT ck_split_bills_payer_share CHECK (payer_share_amount >= 0 AND payer_share_amount <= total_amount),
    CONSTRAINT ck_split_bills_status CHECK (status IN ('OPEN', 'PARTIALLY_SETTLED', 'SETTLED', 'CANCELLED'))
);

CREATE TABLE split_bill_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    split_bill_id UUID NOT NULL REFERENCES split_bills(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    contact VARCHAR(160),
    owed_amount NUMERIC(19,4) NOT NULL,
    settled_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_split_bill_participants_owed CHECK (owed_amount > 0),
    CONSTRAINT ck_split_bill_participants_settled CHECK (settled_amount >= 0 AND settled_amount <= owed_amount),
    CONSTRAINT ck_split_bill_participants_status CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID'))
);

CREATE TABLE split_bill_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES split_bill_participants(id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL UNIQUE REFERENCES financial_transactions(id),
    amount NUMERIC(19,4) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_split_bill_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_split_bills_user_status ON split_bills(user_id, status, occurred_at DESC);
CREATE INDEX idx_split_bill_participants_bill ON split_bill_participants(split_bill_id);
CREATE INDEX idx_split_bill_payments_participant ON split_bill_payments(participant_id, occurred_at DESC);
