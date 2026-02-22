-- Posts table schema for H2 test database
-- This schema is compatible with H2's PostgreSQL mode

CREATE TABLE IF NOT EXISTS posts (
    id BIGINT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    raw TEXT,
    html TEXT,
    excerpt VARCHAR(500),
    cover_image VARCHAR(500),
    status INT NOT NULL DEFAULT 0,
    topic_id BIGINT,
    published_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_archived BOOLEAN DEFAULT FALSE,
    
    -- Stats columns (embedded PostStats)
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    share_count INT DEFAULT 0,
    
    -- Data quality constraints
    CONSTRAINT ck_posts_title_not_blank CHECK (LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_posts_status_valid CHECK (status IN (0, 1, 2, 3))
);

-- Indexes for posts
CREATE INDEX IF NOT EXISTS idx_posts_owner_id ON posts(owner_id);
CREATE INDEX IF NOT EXISTS idx_posts_status ON posts(status);
CREATE INDEX IF NOT EXISTS idx_posts_published_at ON posts(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_topic_id ON posts(topic_id);

-- Comments for posts
COMMENT ON TABLE posts IS '文章表';
COMMENT ON COLUMN posts.id IS '文章ID（雪花算法）';
COMMENT ON COLUMN posts.owner_id IS '作者ID';
COMMENT ON COLUMN posts.title IS '文章标题';
COMMENT ON COLUMN posts.status IS '文章状态：0=DRAFT, 1=PUBLISHED, 2=SCHEDULED, 3=DELETED';

-- Post Stats table schema for H2 test database
CREATE TABLE IF NOT EXISTS post_stats (
    post_id BIGINT PRIMARY KEY,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    view_count BIGINT DEFAULT 0
);

-- Indexes for post_stats
CREATE INDEX IF NOT EXISTS idx_post_stats_like_count ON post_stats(like_count DESC);
CREATE INDEX IF NOT EXISTS idx_post_stats_view_count ON post_stats(view_count DESC);

-- Comments for post_stats
COMMENT ON TABLE post_stats IS '文章统计表';
COMMENT ON COLUMN post_stats.post_id IS '文章ID';
COMMENT ON COLUMN post_stats.like_count IS '点赞数';
COMMENT ON COLUMN post_stats.comment_count IS '评论数';
COMMENT ON COLUMN post_stats.favorite_count IS '收藏数';
COMMENT ON COLUMN post_stats.view_count IS '浏览数';

-- Tags table schema for H2 test database
-- This schema is compatible with H2's PostgreSQL mode

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    slug VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Data quality constraints
    CONSTRAINT ck_tags_name_not_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_tags_slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

-- Indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_slug ON tags(slug);
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);
CREATE INDEX IF NOT EXISTS idx_tags_created_at ON tags(created_at DESC);

-- Comments
COMMENT ON TABLE tags IS '标签表';
COMMENT ON COLUMN tags.id IS '标签ID（雪花算法）';
COMMENT ON COLUMN tags.name IS '标签展示名称';
COMMENT ON COLUMN tags.slug IS 'URL友好标识（唯一）';
COMMENT ON COLUMN tags.description IS '标签描述';

-- Post-Tags association table schema for H2 test database
CREATE TABLE IF NOT EXISTS post_tags (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, tag_id)
);

-- Indexes for post_tags
CREATE INDEX IF NOT EXISTS idx_post_tags_tag_post ON post_tags(tag_id, post_id);
CREATE INDEX IF NOT EXISTS idx_post_tags_post_tag ON post_tags(post_id, tag_id);
CREATE INDEX IF NOT EXISTS idx_post_tags_created_at ON post_tags(created_at DESC);

-- Comments for post_tags
COMMENT ON TABLE post_tags IS '文章标签关联表';
COMMENT ON COLUMN post_tags.post_id IS '文章ID';
COMMENT ON COLUMN post_tags.tag_id IS '标签ID';
