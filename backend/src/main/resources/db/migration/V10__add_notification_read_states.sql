CREATE TABLE notification_read_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    notification_key VARCHAR(320) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_read_states_user_key UNIQUE (user_id, notification_key)
);

CREATE INDEX idx_notification_read_states_user_key
    ON notification_read_states(user_id, notification_key);
