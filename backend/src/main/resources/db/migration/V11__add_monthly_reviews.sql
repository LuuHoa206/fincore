CREATE TABLE monthly_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    reflection VARCHAR(1500),
    next_month_focus VARCHAR(500),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_monthly_reviews_user_period UNIQUE (user_id, period_start)
);

CREATE INDEX idx_monthly_reviews_user_period ON monthly_reviews (user_id, period_start);
