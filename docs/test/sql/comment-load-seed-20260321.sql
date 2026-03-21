-- comment 压测扩容数据
-- 目标文章: 189000000000000101
-- 重复执行安全: 会先删除本脚本固定 ID 段，再重新插入

BEGIN;

DELETE FROM comment_stats
WHERE comment_id BETWEEN 189100000000000001 AND 189100000000000120
   OR comment_id BETWEEN 189100000000100001 AND 189100000000100360;

DELETE FROM comments
WHERE id BETWEEN 189100000000100001 AND 189100000000100360
   OR id BETWEEN 189100000000000001 AND 189100000000000120;

WITH author_pool AS (
    SELECT ARRAY[
        189000000000000001::bigint,
        189000000000000002::bigint,
        189000000000000003::bigint,
        189000000000000004::bigint,
        189000000000000005::bigint
    ] AS authors
),
top_level AS (
    INSERT INTO comments (
        id,
        post_id,
        author_id,
        parent_id,
        root_id,
        reply_to_user_id,
        content,
        status,
        created_at,
        updated_at
    )
    SELECT
        189100000000000000 + seq AS id,
        189000000000000101 AS post_id,
        authors[((seq - 1) % array_length(authors, 1)) + 1] AS author_id,
        NULL AS parent_id,
        189100000000000000 + seq AS root_id,
        NULL AS reply_to_user_id,
        format('压测顶级评论 #%s', seq) AS content,
        0 AS status,
        NOW() - ((120 - seq) || ' seconds')::interval AS created_at,
        NOW() - ((120 - seq) || ' seconds')::interval AS updated_at
    FROM generate_series(1, 120) AS seq
    CROSS JOIN author_pool
    RETURNING id, author_id
),
replies AS (
    INSERT INTO comments (
        id,
        post_id,
        author_id,
        parent_id,
        root_id,
        reply_to_user_id,
        content,
        status,
        created_at,
        updated_at
    )
    SELECT
        189100000000100000 + ((root_seq - 1) * 3) + reply_seq AS id,
        189000000000000101 AS post_id,
        authors[((root_seq + reply_seq - 1) % array_length(authors, 1)) + 1] AS author_id,
        189100000000000000 + root_seq AS parent_id,
        189100000000000000 + root_seq AS root_id,
        authors[((root_seq - 1) % array_length(authors, 1)) + 1] AS reply_to_user_id,
        format('压测回复 #%s-%s', root_seq, reply_seq) AS content,
        0 AS status,
        NOW() - ((120 - root_seq) || ' seconds')::interval + (reply_seq || ' milliseconds')::interval AS created_at,
        NOW() - ((120 - root_seq) || ' seconds')::interval + (reply_seq || ' milliseconds')::interval AS updated_at
    FROM generate_series(1, 120) AS root_seq
    CROSS JOIN generate_series(1, 3) AS reply_seq
    CROSS JOIN author_pool
    RETURNING id
)
INSERT INTO comment_stats (comment_id, like_count, reply_count)
SELECT
    189100000000000000 + seq AS comment_id,
    ((seq * 7) % 31) + 3 AS like_count,
    3 AS reply_count
FROM generate_series(1, 120) AS seq
UNION ALL
SELECT
    189100000000100000 + ((root_seq - 1) * 3) + reply_seq AS comment_id,
    ((root_seq + reply_seq * 3) % 17) + 1 AS like_count,
    0 AS reply_count
FROM generate_series(1, 120) AS root_seq
CROSS JOIN generate_series(1, 3) AS reply_seq;

COMMIT;
