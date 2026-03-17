CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT PRIMARY KEY,
    participant1_id BIGINT NOT NULL,
    participant2_id BIGINT NOT NULL,
    last_message_id BIGINT,
    last_message_content VARCHAR(500),
    last_message_at TIMESTAMPTZ,
    unread_count1 INT NOT NULL DEFAULT 0,
    unread_count2 INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversations_participants
    ON conversations(participant1_id, participant2_id);
CREATE INDEX IF NOT EXISTS idx_conversations_participant1
    ON conversations(participant1_id);
CREATE INDEX IF NOT EXISTS idx_conversations_participant2
    ON conversations(participant2_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at
    ON conversations(last_message_at DESC);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    type SMALLINT NOT NULL DEFAULT 0,
    content VARCHAR(2000) NOT NULL,
    media_url VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    status SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation
    ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender
    ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver
    ON messages(receiver_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at
    ON messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages(conversation_id, created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_messages_conversation'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT fk_messages_conversation
                FOREIGN KEY (conversation_id)
                    REFERENCES conversations(id)
                    ON DELETE CASCADE;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS message_outbox_task (
    id BIGSERIAL PRIMARY KEY,
    task_key VARCHAR(128) NOT NULL UNIQUE,
    task_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_error TEXT,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_by VARCHAR(64),
    claimed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at TIMESTAMP WITH TIME ZONE
);

ALTER TABLE message_outbox_task
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_message_outbox_task_available
    ON message_outbox_task(status, next_attempt_at, created_at, id);
CREATE INDEX IF NOT EXISTS idx_message_outbox_task_processing_claim
    ON message_outbox_task(status, claimed_at)
    WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS idx_message_outbox_task_aggregate_head
    ON message_outbox_task(aggregate_id, created_at, id)
    WHERE status NOT IN ('SUCCEEDED', 'DISPATCHED');
