-- Currency codes are modeled as String fields in JPA. VARCHAR avoids PostgreSQL
-- CHAR padding semantics and keeps schema validation consistent across all tables.
ALTER TABLE app_users
    ALTER COLUMN preferred_currency TYPE VARCHAR(3);

ALTER TABLE wallets
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE money_jars
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE financial_transactions
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE ledger_entries
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE jar_movements
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE allocation_rules
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE budgets
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE recurring_transaction_rules
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE split_bills
    ALTER COLUMN currency TYPE VARCHAR(3);
