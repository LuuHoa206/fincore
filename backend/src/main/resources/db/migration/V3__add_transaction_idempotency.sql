ALTER TABLE financial_transactions
    ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX uk_financial_transactions_user_idempotency
    ON financial_transactions (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
