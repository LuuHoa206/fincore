ALTER TABLE saving_goals
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_saving_goals_open_jar
    ON saving_goals (user_id, jar_id)
    WHERE status IN ('ACTIVE', 'PAUSED');
