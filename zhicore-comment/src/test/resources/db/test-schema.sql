CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    sharding_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10,
    next_attempt_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    error_message TEXT,
    claimed_by VARCHAR(64),
    claimed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_status
    ON outbox_events(status);

CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_created_at
    ON outbox_events(created_at);

CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_retryable
    ON outbox_events(status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_processing_claim
    ON outbox_events(status, claimed_at);

CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_sharding_head
    ON outbox_events(sharding_key, created_at, id);
