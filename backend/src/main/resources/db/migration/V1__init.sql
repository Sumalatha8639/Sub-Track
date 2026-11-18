-- SubTrack initial schema (project plan §5)
-- All timestamps are stored in UTC. All money is INR, DECIMAL(12,2).

CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    phone           VARCHAR(20)  NULL,
    timezone        VARCHAR(50)  NOT NULL DEFAULT 'Asia/Kolkata',
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscriptions (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    user_id         BIGINT         NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    category        VARCHAR(30)    NOT NULL,
    amount          DECIMAL(12, 2) NOT NULL,
    billing_cycle   VARCHAR(20)    NOT NULL,
    start_date      DATE           NOT NULL,
    cycles_elapsed  INT            NOT NULL DEFAULT 0,
    next_due_date   DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    auto_renew      BOOLEAN        NOT NULL DEFAULT FALSE,
    notes           VARCHAR(500)   NULL,
    deleted_at      DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_subscriptions_amount CHECK (amount > 0),
    CONSTRAINT ck_subscriptions_cycles CHECK (cycles_elapsed >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_subscriptions_user ON subscriptions (user_id, deleted_at);
CREATE INDEX ix_subscriptions_status_due ON subscriptions (status, next_due_date);

CREATE TABLE reminder_settings (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    days_before     INT    NOT NULL,
    CONSTRAINT pk_reminder_settings PRIMARY KEY (id),
    CONSTRAINT fk_reminder_settings_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT uk_reminder_settings_sub_days UNIQUE (subscription_id, days_before),
    CONSTRAINT ck_reminder_settings_days CHECK (days_before BETWEEN 1 AND 90)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_periods (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT         NOT NULL,
    due_date        DATE           NOT NULL,
    amount_due      DECIMAL(12, 2) NOT NULL,
    payment_status  VARCHAR(20)    NOT NULL,
    paid_on         DATE           NULL,
    amount_paid     DECIMAL(12, 2) NULL,
    note            VARCHAR(500)   NULL,
    marked_at       DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    CONSTRAINT pk_billing_periods PRIMARY KEY (id),
    CONSTRAINT fk_billing_periods_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT uk_billing_periods_sub_due UNIQUE (subscription_id, due_date),
    CONSTRAINT ck_billing_periods_amount_paid CHECK (amount_paid IS NULL OR amount_paid > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_billing_periods_status_due ON billing_periods (payment_status, due_date);

CREATE TABLE billing_period_history (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    billing_period_id BIGINT      NOT NULL,
    old_status        VARCHAR(20) NOT NULL,
    new_status        VARCHAR(20) NOT NULL,
    changed_at        DATETIME(6) NOT NULL,
    CONSTRAINT pk_billing_period_history PRIMARY KEY (id),
    CONSTRAINT fk_bp_history_period FOREIGN KEY (billing_period_id) REFERENCES billing_periods (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_bp_history_period ON billing_period_history (billing_period_id, changed_at);

CREATE TABLE notification_log (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    billing_period_id BIGINT        NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    days_before       INT           NOT NULL,
    channel           VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    attempt_count     INT           NOT NULL DEFAULT 0,
    next_retry_at     DATETIME(6)   NULL,
    error_message     VARCHAR(1000) NULL,
    created_at        DATETIME(6)   NOT NULL,
    sent_at           DATETIME(6)   NULL,
    CONSTRAINT pk_notification_log PRIMARY KEY (id),
    CONSTRAINT fk_notification_log_period FOREIGN KEY (billing_period_id) REFERENCES billing_periods (id),
    -- Insert-before-send claim key (project plan §7.2)
    CONSTRAINT uk_notification_log_claim UNIQUE (billing_period_id, type, days_before)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_notification_log_retry ON notification_log (status, next_retry_at);

CREATE TABLE refresh_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE user_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    type        VARCHAR(30) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_user_tokens PRIMARY KEY (id),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_user_tokens_hash UNIQUE (token_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_tokens_user_type ON user_tokens (user_id, type);

-- ShedLock: one job run at a time across instances
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
