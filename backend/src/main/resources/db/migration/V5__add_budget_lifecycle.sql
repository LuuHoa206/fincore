ALTER TABLE budgets
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE budgets
    DROP CONSTRAINT uk_budgets_user_category_period;

CREATE UNIQUE INDEX uk_budgets_active_user_category_period
    ON budgets (user_id, category_id, period_start)
    WHERE archived = FALSE;
