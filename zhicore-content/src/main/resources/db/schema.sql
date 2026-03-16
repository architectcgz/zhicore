-- zhicore-content（PostgreSQL）Schema 定义
-- 该文件作为数据库初始化的唯一事实来源（single source of truth）。

CREATE TABLE IF NOT EXISTS posts (
    id BIGINT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    owner_name VARCHAR(255),
    owner_avatar_id VARCHAR(64),
    owner_profile_version BIGINT DEFAULT 0,
    title VARCHAR(255) NOT NULL,
    excerpt TEXT,
    cover_image_id VARCHAR(64),
    status INTEGER NOT NULL DEFAULT 0,
    write_state VARCHAR(32) NOT NULL DEFAULT 'NONE',
    incomplete_reason TEXT,
    topic_id BIGINT,
    published_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_posts_owner_id ON posts(owner_id);
CREATE INDEX IF NOT EXISTS idx_posts_status ON posts(status);
CREATE INDEX IF NOT EXISTS idx_posts_published_at ON posts(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_write_state ON posts(write_state);
CREATE INDEX IF NOT EXISTS idx_posts_owner_profile_version ON posts(owner_id, owner_profile_version);
CREATE INDEX IF NOT EXISTS idx_posts_is_archived ON posts(is_archived);

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categories (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL UNIQUE,
    description TEXT,
    parent_id BIGINT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);

CREATE TABLE IF NOT EXISTS post_tags (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_post_tags_tag_id ON post_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_post_tags_post_id ON post_tags(post_id);

CREATE TABLE IF NOT EXISTS post_stats (
    post_id BIGINT PRIMARY KEY,
    view_count INTEGER NOT NULL DEFAULT 0,
    like_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    favorite_count INTEGER NOT NULL DEFAULT 0,
    share_count INTEGER NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE post_stats ADD COLUMN IF NOT EXISTS favorite_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE post_stats ADD COLUMN IF NOT EXISTS share_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE post_stats ADD COLUMN IF NOT EXISTS last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS tag_stats (
    tag_id BIGINT PRIMARY KEY,
    post_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS consumed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_consumed_events_consumed_at ON consumed_events(consumed_at);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(32) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claimed_by VARCHAR(128),
    dispatched_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_status_created ON outbox_event(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_status_attempt ON outbox_event(status, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_event_id ON outbox_event(event_id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_aggregate ON outbox_event(aggregate_id, aggregate_version);
CREATE INDEX IF NOT EXISTS idx_outbox_event_aggregate_order ON outbox_event(aggregate_id, aggregate_version, created_at, id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_updated_at ON outbox_event(updated_at);

CREATE TABLE IF NOT EXISTS domain_event_task (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(32) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id BIGINT,
    aggregate_version BIGINT,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload TEXT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claimed_by VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_domain_event_task_status_attempt
    ON domain_event_task(status, next_attempt_at, priority, created_at);
CREATE INDEX IF NOT EXISTS idx_domain_event_task_aggregate_order
    ON domain_event_task(aggregate_id, aggregate_version, created_at, id);

-- ==================== Outbox 手动重试审计（R14） ====================
CREATE TABLE IF NOT EXISTS outbox_retry_audit (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    reason TEXT,
    retried_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_retry_audit_event_id_retried_at
    ON outbox_retry_audit(event_id, retried_at DESC);

-- ==================== 定时发布事件（R1） ====================
CREATE TABLE IF NOT EXISTS scheduled_publish_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(32) NOT NULL UNIQUE,
    trigger_event_id VARCHAR(32),
    post_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reschedule_retry_count INTEGER NOT NULL DEFAULT 0,
    publish_retry_count INTEGER NOT NULL DEFAULT 0,
    claimed_at TIMESTAMP,
    claimed_by VARCHAR(128),
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_spe_status_attempt ON scheduled_publish_event(status, next_attempt_at, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_spe_trigger_event_id ON scheduled_publish_event(trigger_event_id);
CREATE INDEX IF NOT EXISTS idx_spe_post_id ON scheduled_publish_event(post_id);

-- ==================== 点赞/收藏（R2/R3） ====================
CREATE TABLE IF NOT EXISTS post_likes (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_likes_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_likes_post_id ON post_likes(post_id);
CREATE INDEX IF NOT EXISTS idx_post_likes_user_id_created_at ON post_likes(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS post_favorites (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_favorites_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_favorites_post_id ON post_favorites(post_id);
CREATE INDEX IF NOT EXISTS idx_post_favorites_user_id_created_at ON post_favorites(user_id, created_at DESC);
