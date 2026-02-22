-- =====================================================
-- Initialize Post Stats Script
-- 初始化文章统计脚本
-- 
-- 说明：此脚本为所有已发布的文章创建 post_stats 记录
-- 功能：
-- 1. 为每篇文章创建 post_stats 记录
-- 2. 初始化所有统计字段为 0
-- 
-- Requirements: 5.1
-- =====================================================

-- 开始事务
BEGIN;

-- 为所有文章创建 post_stats 记录（如果不存在）
INSERT INTO post_stats (post_id, view_count, like_count, favorite_count, comment_count)
SELECT 
    id as post_id,
    0 as view_count,
    0 as like_count,
    0 as favorite_count,
    0 as comment_count
FROM posts
WHERE NOT EXISTS (
    SELECT 1 FROM post_stats WHERE post_stats.post_id = posts.id
)
ON CONFLICT (post_id) DO NOTHING;

-- 验证结果
DO $$
DECLARE
    post_count INT;
    stats_count INT;
BEGIN
    -- 获取文章总数
    SELECT COUNT(*) INTO post_count FROM posts;
    
    -- 获取统计记录总数
    SELECT COUNT(*) INTO stats_count FROM post_stats;
    
    -- 输出结果
    RAISE NOTICE '文章总数: %', post_count;
    RAISE NOTICE '统计记录总数: %', stats_count;
    
    -- 验证每篇文章都有统计记录
    IF stats_count < post_count THEN
        RAISE WARNING '部分文章缺少统计记录';
    ELSE
        RAISE NOTICE '✓ 所有文章都有统计记录';
    END IF;
END $$;

-- 提交事务
COMMIT;

-- =====================================================
-- 使用说明
-- =====================================================

/*
执行此脚本：

1. 使用 psql 命令行：
   psql -h localhost -p 5432 -U postgres -d ZhiCore -f init-post-stats.sql

2. 使用 PowerShell：
   $env:PGPASSWORD="postgres123456"
   psql -h localhost -p 5432 -U postgres -d ZhiCore -f init-post-stats.sql

前置条件：
- PostgreSQL 服务已启动
- ZhiCore 数据库已创建
- posts 表已存在且包含数据

验证需求：
- Requirements 5.1: 为每篇文章创建 post_stats 记录
*/
