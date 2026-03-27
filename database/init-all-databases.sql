-- =====================================================
-- ZhiCore Microservices Database Initialization Script
-- 博客微服务系统数据库初始化脚本
-- 
-- 说明：此脚本用于快速初始化所有服务的数据库表结构
-- 执行方式：在 PostgreSQL 中依次为每个数据库执行对应的部分
-- =====================================================

-- =====================================================
-- 1. User Service (ZhiCore_user 数据库)
-- =====================================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    nick_name VARCHAR(50) NOT NULL DEFAULT '',
    email VARCHAR(255),
    email_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    allow_stranger_message BOOLEAN NOT NULL DEFAULT TRUE,
    password_hash VARCHAR(255),
    phone_number VARCHAR(20),
    phone_number_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_id VARCHAR(36),
    bio VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS allow_stranger_message BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users(deleted) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_users_is_active_all ON users (is_active);
CREATE INDEX IF NOT EXISTS idx_users_username_pattern ON users USING btree (username text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_is_active_created_at ON users (is_active, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_avatar_id ON users(avatar_id);

COMMENT ON COLUMN users.avatar_id IS '用户头像文件ID（UUIDv7格式）';

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_roles_name ON roles(name);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

-- 用户关注表
CREATE TABLE IF NOT EXISTS user_follows (
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id)
);

CREATE INDEX IF NOT EXISTS idx_user_follows_follower ON user_follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_user_follows_following ON user_follows(following_id);

-- 用户关注统计表
CREATE TABLE IF NOT EXISTS user_follow_stats (
    user_id BIGINT PRIMARY KEY,
    followers_count INT NOT NULL DEFAULT 0,
    following_count INT NOT NULL DEFAULT 0
);

-- 用户拉黑表
CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id BIGINT NOT NULL,
    blocked_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker ON user_blocks(blocker_id);
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON user_blocks(blocked_id);

-- 用户签到表
CREATE TABLE IF NOT EXISTS user_check_ins (
    user_id BIGINT NOT NULL,
    check_in_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, check_in_date)
);

CREATE INDEX IF NOT EXISTS idx_user_check_ins_user ON user_check_ins(user_id);

-- 用户签到统计表
CREATE TABLE IF NOT EXISTS user_check_in_stats (
    user_id BIGINT PRIMARY KEY,
    total_days INT NOT NULL DEFAULT 0,
    continuous_days INT NOT NULL DEFAULT 0,
    max_continuous_days INT NOT NULL DEFAULT 0,
    last_check_in_date DATE
);

-- 初始化默认角色
INSERT INTO roles (id, name, description) VALUES
    (1, 'ADMIN', '系统管理员'),
    (2, 'MODERATOR', '内容审核员'),
    (3, 'USER', '普通用户')
ON CONFLICT (id) DO NOTHING;

SELECT setval('roles_id_seq', 10, false);

-- Transactional Outbox 事件表
CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    sharding_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    error_message TEXT
);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON outbox_events(status);
CREATE INDEX IF NOT EXISTS idx_outbox_events_created_at ON outbox_events(created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_events_retryable ON outbox_events(status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_outbox_events_processing_claim ON outbox_events(status, claimed_at)
    WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS idx_outbox_events_sharding_head ON outbox_events(sharding_key, created_at, id)
    WHERE status NOT IN ('SUCCEEDED', 'SENT');

COMMENT ON TABLE outbox_events IS 'Transactional Outbox 事件表';
COMMENT ON COLUMN outbox_events.id IS '事件ID（UUID格式）';
COMMENT ON COLUMN outbox_events.topic IS 'RocketMQ Topic';
COMMENT ON COLUMN outbox_events.tag IS 'RocketMQ Tag';
COMMENT ON COLUMN outbox_events.sharding_key IS '分片键（用于消息路由）';
COMMENT ON COLUMN outbox_events.payload IS '事件负载（JSON格式）';
COMMENT ON COLUMN outbox_events.status IS '事件状态：PENDING（待发送）, PROCESSING（处理中）, SUCCEEDED（已成功收敛）, FAILED（重试中）, DEAD（死信）';
COMMENT ON COLUMN outbox_events.retry_count IS '重试次数';
COMMENT ON COLUMN outbox_events.max_retries IS '最大重试次数（默认10）';
COMMENT ON COLUMN outbox_events.next_attempt_at IS '下次允许被处理时间（指数退避）';
COMMENT ON COLUMN outbox_events.created_at IS '创建时间';
COMMENT ON COLUMN outbox_events.sent_at IS '发送时间';
COMMENT ON COLUMN outbox_events.error_message IS '错误信息';
COMMENT ON COLUMN outbox_events.claimed_by IS '当前 claim 该事件的 worker 标识';
COMMENT ON COLUMN outbox_events.claimed_at IS '当前 claim 时间';

-- =====================================================
-- 2. Post Service (ZhiCore_post 数据库)
-- =====================================================

-- 文章表
CREATE TABLE IF NOT EXISTS posts (
    id BIGINT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    raw TEXT,
    html TEXT,
    excerpt VARCHAR(500),
    status SMALLINT NOT NULL DEFAULT 0,
    topic_id BIGINT,
    cover_image_id VARCHAR(36),
    published_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_name VARCHAR(50) NOT NULL DEFAULT '未知用户',
    owner_avatar_id VARCHAR(36),
    owner_profile_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_posts_owner ON posts(owner_id);
CREATE INDEX IF NOT EXISTS idx_posts_status ON posts(status);
CREATE INDEX IF NOT EXISTS idx_posts_topic ON posts(topic_id);
CREATE INDEX IF NOT EXISTS idx_posts_published_at ON posts(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_scheduled_at ON posts(scheduled_at) WHERE scheduled_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_posts_title ON posts USING btree (title text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_posts_status_created_at ON posts (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_owner_status_created_at ON posts (owner_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_cover_image_id ON posts(cover_image_id);
CREATE INDEX IF NOT EXISTS idx_posts_owner_name_version ON posts(owner_name, owner_profile_version) WHERE owner_name = '未知用户' AND owner_profile_version = 0;

COMMENT ON COLUMN posts.cover_image_id IS '封面图片文件ID（UUIDv7格式）';
COMMENT ON COLUMN posts.owner_name IS '作者昵称快照（冗余字段）';
COMMENT ON COLUMN posts.owner_avatar_id IS '作者头像文件ID快照（冗余字段，UUIDv7格式）';
COMMENT ON COLUMN posts.owner_profile_version IS '作者资料版本号（用于防止消息乱序）';

-- 文章统计表
CREATE TABLE IF NOT EXISTS post_stats (
    post_id BIGINT PRIMARY KEY,
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    share_count INT NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 文章点赞表
CREATE TABLE IF NOT EXISTS post_likes (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_likes_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_likes_user ON post_likes(user_id);
CREATE INDEX IF NOT EXISTS idx_post_likes_post ON post_likes(post_id);
CREATE INDEX IF NOT EXISTS idx_post_likes_user_id_created_at ON post_likes(user_id, created_at DESC);

-- 文章收藏表
CREATE TABLE IF NOT EXISTS post_favorites (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_favorites_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_favorites_user ON post_favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_post_favorites_post ON post_favorites(post_id);
CREATE INDEX IF NOT EXISTS idx_post_favorites_created_at ON post_favorites(created_at DESC);

-- =====================================================
-- 3. Comment Service (ZhiCore_comment 数据库)
-- =====================================================

-- 评论表
CREATE TABLE IF NOT EXISTS comments (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    parent_id BIGINT,
    root_id BIGINT NOT NULL,
    reply_to_user_id BIGINT,
    content VARCHAR(2000) NOT NULL DEFAULT '',
    image_ids TEXT[],
    voice_id VARCHAR(36),
    voice_duration INTEGER,
    status SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comments_post ON comments(post_id);
CREATE INDEX IF NOT EXISTS idx_comments_author ON comments(author_id);
CREATE INDEX IF NOT EXISTS idx_comments_root ON comments(root_id);
CREATE INDEX IF NOT EXISTS idx_comments_parent ON comments(parent_id);
CREATE INDEX IF NOT EXISTS idx_comments_post_status ON comments(post_id, status);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON comments(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_post_top_time_active
    ON comments(post_id, created_at DESC, id DESC)
    WHERE parent_id IS NULL AND status = 0;
CREATE INDEX IF NOT EXISTS idx_comments_root_reply_time_active
    ON comments(root_id, created_at ASC, id ASC)
    WHERE parent_id IS NOT NULL AND status = 0;

COMMENT ON COLUMN comments.image_ids IS '评论图片文件ID数组（UUIDv7格式）';
COMMENT ON COLUMN comments.voice_id IS '评论语音文件ID（UUIDv7格式）';
COMMENT ON COLUMN comments.voice_duration IS '评论语音时长（秒）';

-- 评论统计表
CREATE TABLE IF NOT EXISTS comment_stats (
    comment_id BIGINT PRIMARY KEY,
    like_count INT NOT NULL DEFAULT 0,
    reply_count INT NOT NULL DEFAULT 0
);

-- 评论点赞表
CREATE TABLE IF NOT EXISTS comment_likes (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_comment_likes_user ON comment_likes(user_id);
CREATE INDEX IF NOT EXISTS idx_comment_likes_comment ON comment_likes(comment_id);

-- 评论服务 Transactional Outbox 事件表
CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    sharding_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    error_message TEXT
);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_status ON outbox_events(status);
CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_created_at ON outbox_events(created_at);
CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_retryable ON outbox_events(status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_processing_claim ON outbox_events(status, claimed_at)
    WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS idx_comment_outbox_events_sharding_head ON outbox_events(sharding_key, created_at, id)
    WHERE status NOT IN ('SUCCEEDED', 'SENT');

-- =====================================================
-- 4. Message Service (ZhiCore_message 数据库)
-- =====================================================

-- 会话表
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

CREATE UNIQUE INDEX IF NOT EXISTS idx_conversations_participants ON conversations(participant1_id, participant2_id);
CREATE INDEX IF NOT EXISTS idx_conversations_participant1 ON conversations(participant1_id);
CREATE INDEX IF NOT EXISTS idx_conversations_participant2 ON conversations(participant2_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at ON conversations(last_message_at DESC);

-- 私信消息表
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

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_receiver ON messages(receiver_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages(conversation_id, created_at DESC);

-- 外键约束
ALTER TABLE messages 
ADD CONSTRAINT IF NOT EXISTS fk_messages_conversation 
FOREIGN KEY (conversation_id) 
REFERENCES conversations(id) 
ON DELETE CASCADE;

-- Message outbox 任务表
CREATE TABLE IF NOT EXISTS message_outbox_task (
    id BIGSERIAL PRIMARY KEY,
    task_key VARCHAR(128) NOT NULL UNIQUE,
    task_type VARCHAR(32) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_by VARCHAR(64),
    claimed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_message_outbox_task_available
    ON message_outbox_task(status, next_attempt_at, created_at, id);
CREATE INDEX IF NOT EXISTS idx_message_outbox_task_processing_claim
    ON message_outbox_task(status, claimed_at) WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS idx_message_outbox_task_aggregate_head
    ON message_outbox_task(aggregate_id, created_at, id) WHERE status NOT IN ('SUCCEEDED', 'DISPATCHED');

-- =====================================================
-- 5. Notification Service (ZhiCore_notification 数据库)
-- =====================================================

-- 通知表
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    type SMALLINT NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'INTERACTION',
    event_code VARCHAR(64) NOT NULL DEFAULT '',
    metadata TEXT,
    actor_id BIGINT,
    target_type VARCHAR(50),
    target_id BIGINT,
    content VARCHAR(2000) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'INTERACTION',
    ADD COLUMN IF NOT EXISTS event_code VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS metadata TEXT;

UPDATE notifications
SET category = CASE type
        WHEN 4 THEN 'SYSTEM'
        ELSE 'INTERACTION'
    END,
    event_code = CASE type
        WHEN 0 THEN 'interaction.like'
        WHEN 1 THEN 'interaction.comment'
        WHEN 2 THEN 'interaction.follow'
        WHEN 3 THEN 'interaction.reply'
        WHEN 4 THEN 'system.notice'
        ELSE event_code
    END
WHERE COALESCE(NULLIF(BTRIM(category), ''), '') = ''
   OR COALESCE(NULLIF(BTRIM(event_code), ''), '') = ''
   OR LOWER(BTRIM(event_code)) IN ('__legacy__', 'legacy.default')
   OR (type = 4 AND category = 'INTERACTION');

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read ON notifications(recipient_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_type ON notifications(recipient_id, type);
CREATE INDEX IF NOT EXISTS idx_notifications_event_code ON notifications(event_code);

CREATE TABLE IF NOT EXISTS notification_user_preference (
    user_id BIGINT PRIMARY KEY,
    like_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    comment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    follow_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reply_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    system_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_user_dnd (
    user_id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    start_time VARCHAR(5),
    end_time VARCHAR(5),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 全局公告表
CREATE TABLE IF NOT EXISTS global_announcements (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    is_markdown BOOLEAN NOT NULL DEFAULT FALSE,
    type INT NOT NULL DEFAULT 0,
    link VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id BIGINT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    priority INT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_global_announcements_enabled ON global_announcements(is_enabled);
CREATE INDEX IF NOT EXISTS idx_global_announcements_priority ON global_announcements(priority DESC);

-- 小助手消息表
CREATE TABLE IF NOT EXISTS assistant_messages (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    link VARCHAR(500),
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_assistant_messages_user ON assistant_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_assistant_messages_user_read ON assistant_messages(user_id, is_read);

-- =====================================================
-- 6. Admin Service (ZhiCore_admin 数据库)
-- =====================================================

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_operator_created_at
    ON audit_logs(operator_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target_created_at
    ON audit_logs(target_type, target_id, created_at DESC);

-- 举报表
CREATE TABLE IF NOT EXISTS reports (
    id BIGINT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    reported_user_id BIGINT,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    handler_id BIGINT,
    handle_action VARCHAR(32),
    handle_remark TEXT,
    handled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reports_status_created_at
    ON reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_created_at
    ON reports(reporter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_target
    ON reports(target_type, target_id);

-- =====================================================
-- 7. Tag Feature (ZhiCore_post 数据库 - 标签功能扩展)
-- =====================================================

-- 标签表
CREATE TABLE IF NOT EXISTS tags (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    slug VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 数据质量约束
    CONSTRAINT ck_tags_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_tags_slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

-- 标签索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_slug ON tags(slug);
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);
CREATE INDEX IF NOT EXISTS idx_tags_created_at ON tags(created_at DESC);

-- 标签注释
COMMENT ON TABLE tags IS '标签表';
COMMENT ON COLUMN tags.id IS '标签ID（雪花算法）';
COMMENT ON COLUMN tags.name IS '标签展示名称';
COMMENT ON COLUMN tags.slug IS 'URL友好标识（唯一）';
COMMENT ON COLUMN tags.description IS '标签描述';
COMMENT ON CONSTRAINT ck_tags_name_not_blank ON tags IS 'name 不允许为空或仅包含空白';
COMMENT ON CONSTRAINT ck_tags_slug_format ON tags IS 'slug 仅允许小写字母、数字和单个连字符';

-- 文章标签关联表
CREATE TABLE IF NOT EXISTS post_tags (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, tag_id)
);

-- 文章标签关联索引
CREATE INDEX IF NOT EXISTS idx_post_tags_tag_post ON post_tags(tag_id, post_id);
CREATE INDEX IF NOT EXISTS idx_post_tags_post_tag ON post_tags(post_id, tag_id);
CREATE INDEX IF NOT EXISTS idx_post_tags_created_at ON post_tags(created_at DESC);

-- 文章标签关联注释
COMMENT ON TABLE post_tags IS '文章标签关联表';
COMMENT ON COLUMN post_tags.post_id IS '文章ID';
COMMENT ON COLUMN post_tags.tag_id IS '标签ID';

-- 文章标签关联外键约束
-- 注意：使用逻辑外键，不创建物理外键约束，数据完整性由业务层控制
-- 这样可以提供更好的灵活性，避免数据库锁竞争，适合微服务架构

-- 标签统计表（派生数据，用于性能优化）
CREATE TABLE IF NOT EXISTS tag_stats (
    tag_id BIGINT PRIMARY KEY,
    post_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 标签统计索引
CREATE INDEX IF NOT EXISTS idx_tag_stats_post_count ON tag_stats(post_count DESC);

-- 标签统计注释
COMMENT ON TABLE tag_stats IS '标签统计表（派生数据）';
COMMENT ON COLUMN tag_stats.tag_id IS '标签ID';
COMMENT ON COLUMN tag_stats.post_count IS '文章数量';

-- 标签统计外键约束
-- 注意：使用逻辑外键，不创建物理外键约束，数据完整性由业务层控制

-- =====================================================
-- 数据库初始化完成
-- =====================================================
