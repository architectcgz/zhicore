\set ON_ERROR_STOP on

-- =====================================================
-- ZhiCore backend smoke test data
-- 说明：
-- 1. 使用固定 ID，支持重复执行
-- 2. 覆盖用户、关注、文章、标签、互动、评论、私信
-- 3. MongoDB 读模型数据请配合 seed-backend-smoke-data.mongo.js 一起执行
-- =====================================================

\connect zhicore_user

BEGIN;

DELETE FROM user_roles
WHERE user_id IN (
    189000000000000001,
    189000000000000002,
    189000000000000003,
    189000000000000004,
    189000000000000005
);

DELETE FROM user_follows
WHERE follower_id IN (
    189000000000000001,
    189000000000000002,
    189000000000000003,
    189000000000000004,
    189000000000000005
) OR following_id IN (
    189000000000000001,
    189000000000000002,
    189000000000000003,
    189000000000000004,
    189000000000000005
);

DELETE FROM user_follow_stats
WHERE user_id IN (
    189000000000000001,
    189000000000000002,
    189000000000000003,
    189000000000000004,
    189000000000000005
);

DELETE FROM user_check_in_stats
WHERE user_id IN (
    189000000000000001,
    189000000000000002,
    189000000000000003,
    189000000000000004,
    189000000000000005
);

DELETE FROM user_check_ins
WHERE user_id IN (
    189000000000000001,
    189000000000000002,
    189000000000000003,
    189000000000000004,
    189000000000000005
);

DELETE FROM users
WHERE id IN (
    189000000000000001,
    189000000000000002,
    189000000000000003,
    189000000000000004,
    189000000000000005
) OR username LIKE 'test_seed_%';

INSERT INTO users (
    id,
    username,
    nick_name,
    email,
    email_confirmed,
    allow_stranger_message,
    password_hash,
    phone_number,
    phone_number_confirmed,
    avatar_id,
    bio,
    is_active,
    created_at,
    updated_at,
    deleted,
    profile_version
) VALUES
(
    189000000000000001,
    'test_seed_admin',
    '测试管理员',
    'test_seed_admin@zhicore.local',
    true,
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800010001',
    true,
    null,
    '用于后端联调的固定管理员账号。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '30 days',
    CURRENT_TIMESTAMP - INTERVAL '2 hours',
    false,
    1
),
(
    189000000000000002,
    'test_seed_author',
    '测试作者',
    'test_seed_author@zhicore.local',
    true,
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800010002',
    true,
    null,
    '用于文章、评论和私信链路联调的作者账号。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '20 days',
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    false,
    1
),
(
    189000000000000003,
    'test_seed_moderator',
    '测试审核员',
    'test_seed_moderator@zhicore.local',
    true,
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800010003',
    true,
    null,
    '用于审核和互动链路联调的审核员账号。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '18 days',
    CURRENT_TIMESTAMP - INTERVAL '3 hours',
    false,
    1
),
(
    189000000000000004,
    'test_seed_reader_a',
    '测试读者A',
    'test_seed_reader_a@zhicore.local',
    true,
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800010004',
    true,
    null,
    '用于点赞、收藏、评论和私信的读者账号 A。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '12 days',
    CURRENT_TIMESTAMP - INTERVAL '5 hours',
    false,
    1
),
(
    189000000000000005,
    'test_seed_reader_b',
    '测试读者B',
    'test_seed_reader_b@zhicore.local',
    true,
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800010005',
    true,
    null,
    '用于互动统计联调的读者账号 B。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '10 days',
    CURRENT_TIMESTAMP - INTERVAL '6 hours',
    false,
    1
);

INSERT INTO user_roles (user_id, role_id) VALUES
(189000000000000001, 1),
(189000000000000002, 3),
(189000000000000003, 2),
(189000000000000004, 3),
(189000000000000005, 3);

INSERT INTO user_follows (follower_id, following_id, created_at) VALUES
(189000000000000003, 189000000000000002, CURRENT_TIMESTAMP - INTERVAL '7 days'),
(189000000000000004, 189000000000000002, CURRENT_TIMESTAMP - INTERVAL '6 days'),
(189000000000000005, 189000000000000002, CURRENT_TIMESTAMP - INTERVAL '5 days'),
(189000000000000002, 189000000000000001, CURRENT_TIMESTAMP - INTERVAL '4 days');

INSERT INTO user_follow_stats (user_id, followers_count, following_count) VALUES
(189000000000000001, 1, 0),
(189000000000000002, 3, 1),
(189000000000000003, 0, 1),
(189000000000000004, 0, 1),
(189000000000000005, 0, 1);

COMMIT;

\connect zhicore_content

BEGIN;

DELETE FROM post_likes
WHERE id IN (
    189000000000000301,
    189000000000000302,
    189000000000000303,
    189000000000000304,
    189000000000000305
);

DELETE FROM post_favorites
WHERE id IN (
    189000000000000401,
    189000000000000402,
    189000000000000403
);

DELETE FROM post_tags
WHERE post_id IN (
    189000000000000101,
    189000000000000102,
    189000000000000103
) OR tag_id IN (
    189000000000000201,
    189000000000000202,
    189000000000000203
);

DELETE FROM post_stats
WHERE post_id IN (
    189000000000000101,
    189000000000000102,
    189000000000000103
);

DELETE FROM posts
WHERE id IN (
    189000000000000101,
    189000000000000102,
    189000000000000103
);

DELETE FROM tags
WHERE id IN (
    189000000000000201,
    189000000000000202,
    189000000000000203
) OR slug IN (
    'seed-backend',
    'seed-smoke',
    'seed-social'
);

INSERT INTO tags (id, name, slug, description, created_at, updated_at) VALUES
(189000000000000201, '后端联调', 'seed-backend', '用于后端联调和接口冒烟验证的固定标签。', CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '8 days'),
(189000000000000202, '冒烟测试', 'seed-smoke', '用于基础功能验证的冒烟测试标签。', CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '8 days'),
(189000000000000203, '社区互动', 'seed-social', '用于评论、点赞、收藏和私信链路的演示标签。', CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '8 days');

INSERT INTO posts (
    id,
    owner_id,
    owner_name,
    owner_avatar_id,
    owner_profile_version,
    title,
    excerpt,
    cover_image_id,
    status,
    write_state,
    incomplete_reason,
    topic_id,
    published_at,
    scheduled_at,
    created_at,
    updated_at,
    is_archived,
    version
) VALUES
(
    189000000000000101,
    189000000000000002,
    '测试作者',
    null,
    1,
    'ZhiCore 后端联调文章 A',
    '这是一篇用于验证用户、内容、评论和互动链路的固定已发布文章。',
    null,
    1,
    'PUBLISHED',
    null,
    null,
    CURRENT_TIMESTAMP - INTERVAL '3 days',
    null,
    CURRENT_TIMESTAMP - INTERVAL '4 days',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    false,
    1
),
(
    189000000000000102,
    189000000000000002,
    '测试作者',
    null,
    1,
    'ZhiCore 草稿联调文章 B',
    '这是一篇仅保存内容但未发布的草稿文章，用于验证草稿链路。',
    null,
    0,
    'CONTENT_SAVED',
    null,
    null,
    null,
    null,
    CURRENT_TIMESTAMP - INTERVAL '18 hours',
    CURRENT_TIMESTAMP - INTERVAL '2 hours',
    false,
    1
),
(
    189000000000000103,
    189000000000000003,
    '测试审核员',
    null,
    1,
    'ZhiCore 社区互动演示文章 C',
    '这是一篇用于验证审核员发文、点赞、收藏和评论统计的文章。',
    null,
    1,
    'PUBLISHED',
    null,
    null,
    CURRENT_TIMESTAMP - INTERVAL '26 hours',
    null,
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    CURRENT_TIMESTAMP - INTERVAL '3 hours',
    false,
    1
);

INSERT INTO post_stats (
    post_id,
    view_count,
    like_count,
    comment_count,
    favorite_count,
    share_count,
    last_updated_at
) VALUES
(189000000000000101, 128, 3, 3, 2, 0, CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
(189000000000000102, 17, 0, 0, 0, 0, CURRENT_TIMESTAMP - INTERVAL '2 hours'),
(189000000000000103, 64, 2, 1, 1, 0, CURRENT_TIMESTAMP - INTERVAL '15 minutes');

INSERT INTO post_tags (post_id, tag_id, created_at) VALUES
(189000000000000101, 189000000000000201, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(189000000000000101, 189000000000000202, CURRENT_TIMESTAMP - INTERVAL '3 days'),
(189000000000000102, 189000000000000202, CURRENT_TIMESTAMP - INTERVAL '18 hours'),
(189000000000000103, 189000000000000201, CURRENT_TIMESTAMP - INTERVAL '26 hours'),
(189000000000000103, 189000000000000203, CURRENT_TIMESTAMP - INTERVAL '26 hours');

INSERT INTO post_likes (id, post_id, user_id, created_at) VALUES
(189000000000000301, 189000000000000101, 189000000000000003, CURRENT_TIMESTAMP - INTERVAL '2 days'),
(189000000000000302, 189000000000000101, 189000000000000004, CURRENT_TIMESTAMP - INTERVAL '40 hours'),
(189000000000000303, 189000000000000101, 189000000000000005, CURRENT_TIMESTAMP - INTERVAL '20 hours'),
(189000000000000304, 189000000000000103, 189000000000000001, CURRENT_TIMESTAMP - INTERVAL '18 hours'),
(189000000000000305, 189000000000000103, 189000000000000004, CURRENT_TIMESTAMP - INTERVAL '12 hours');

INSERT INTO post_favorites (id, post_id, user_id, created_at) VALUES
(189000000000000401, 189000000000000101, 189000000000000004, CURRENT_TIMESTAMP - INTERVAL '36 hours'),
(189000000000000402, 189000000000000101, 189000000000000005, CURRENT_TIMESTAMP - INTERVAL '16 hours'),
(189000000000000403, 189000000000000103, 189000000000000001, CURRENT_TIMESTAMP - INTERVAL '10 hours');

COMMIT;

\connect zhicore_comment

BEGIN;

DELETE FROM comment_likes
WHERE comment_id IN (
    189000000000000501,
    189000000000000502,
    189000000000000503,
    189000000000000504
);

DELETE FROM comment_stats
WHERE comment_id IN (
    189000000000000501,
    189000000000000502,
    189000000000000503,
    189000000000000504
);

DELETE FROM comments
WHERE id IN (
    189000000000000501,
    189000000000000502,
    189000000000000503,
    189000000000000504
);

INSERT INTO comments (
    id,
    post_id,
    author_id,
    parent_id,
    root_id,
    reply_to_user_id,
    content,
    image_ids,
    status,
    created_at,
    updated_at,
    voice_id,
    voice_duration
) VALUES
(
    189000000000000501,
    189000000000000101,
    189000000000000004,
    null,
    189000000000000501,
    null,
    '这篇文章已经覆盖到了用户、内容和互动三个链路，适合做接口回归。',
    null,
    0,
    CURRENT_TIMESTAMP - INTERVAL '30 hours',
    CURRENT_TIMESTAMP - INTERVAL '30 hours',
    null,
    null
),
(
    189000000000000502,
    189000000000000101,
    189000000000000002,
    189000000000000501,
    189000000000000501,
    189000000000000004,
    '收到，这条评论会作为作者回复链路的固定样例保留。',
    null,
    0,
    CURRENT_TIMESTAMP - INTERVAL '29 hours',
    CURRENT_TIMESTAMP - INTERVAL '29 hours',
    null,
    null
),
(
    189000000000000503,
    189000000000000101,
    189000000000000003,
    null,
    189000000000000503,
    null,
    '审核视角看这篇数据也够完整，后续可以继续叠加敏感词和隐藏场景。',
    null,
    0,
    CURRENT_TIMESTAMP - INTERVAL '20 hours',
    CURRENT_TIMESTAMP - INTERVAL '20 hours',
    null,
    null
),
(
    189000000000000504,
    189000000000000103,
    189000000000000005,
    null,
    189000000000000504,
    null,
    '这篇文章的点赞和收藏数量与统计表保持一致，适合校验聚合结果。',
    null,
    0,
    CURRENT_TIMESTAMP - INTERVAL '8 hours',
    CURRENT_TIMESTAMP - INTERVAL '8 hours',
    null,
    null
);

INSERT INTO comment_stats (comment_id, like_count, reply_count) VALUES
(189000000000000501, 1, 1),
(189000000000000502, 1, 0),
(189000000000000503, 0, 0),
(189000000000000504, 1, 0);

INSERT INTO comment_likes (comment_id, user_id, created_at) VALUES
(189000000000000501, 189000000000000001, CURRENT_TIMESTAMP - INTERVAL '24 hours'),
(189000000000000502, 189000000000000005, CURRENT_TIMESTAMP - INTERVAL '18 hours'),
(189000000000000504, 189000000000000002, CURRENT_TIMESTAMP - INTERVAL '6 hours');

COMMIT;

\connect zhicore_message

BEGIN;

DELETE FROM messages
WHERE id IN (
    189000000000000611,
    189000000000000612,
    189000000000000613
);

DELETE FROM conversations
WHERE id = 189000000000000601;

INSERT INTO conversations (
    id,
    participant1_id,
    participant2_id,
    last_message_id,
    last_message_content,
    last_message_at,
    unread_count1,
    unread_count2,
    created_at
) VALUES
(
    189000000000000601,
    189000000000000002,
    189000000000000004,
    189000000000000613,
    '我已经把后端联调数据补齐了，你可以开始验证接口。',
    CURRENT_TIMESTAMP - INTERVAL '2 hours',
    0,
    1,
    CURRENT_TIMESTAMP - INTERVAL '1 day'
);

INSERT INTO messages (
    id,
    conversation_id,
    sender_id,
    receiver_id,
    type,
    content,
    media_url,
    is_read,
    read_at,
    status,
    created_at
) VALUES
(
    189000000000000611,
    189000000000000601,
    189000000000000004,
    189000000000000002,
    0,
    '我已经看到联调文章 A 了，评论和收藏也能串起来。',
    null,
    true,
    CURRENT_TIMESTAMP - INTERVAL '5 hours',
    0,
    CURRENT_TIMESTAMP - INTERVAL '6 hours'
),
(
    189000000000000612,
    189000000000000601,
    189000000000000002,
    189000000000000004,
    0,
    '好的，我再补一条作者回复和一条未读私信，方便你验证会话列表。',
    null,
    true,
    CURRENT_TIMESTAMP - INTERVAL '4 hours',
    0,
    CURRENT_TIMESTAMP - INTERVAL '4 hours'
),
(
    189000000000000613,
    189000000000000601,
    189000000000000002,
    189000000000000004,
    0,
    '我已经把后端联调数据补齐了，你可以开始验证接口。',
    null,
    false,
    null,
    0,
    CURRENT_TIMESTAMP - INTERVAL '2 hours'
);

COMMIT;

-- 说明：
-- zhicore_notification 当前环境尚未初始化表结构，本脚本不对其做变更。
