-- =====================================================
-- Generate Post Views Script
-- 生成文章浏览记录脚本
-- 
-- 说明：此脚本为已发布的文章生成浏览记录
-- 功能：
-- 1. 为已发布文章生成 10-100 次浏览记录
-- 2. 更新 post_stats 的 view_count
-- 
-- Requirements: 5.2
-- =====================================================

-- 开始事务
BEGIN;

-- 为每篇已发布文章生成随机浏览数（10-100）
UPDATE post_stats
SET view_count = floor(random() * 91 + 10)::int
WHERE post_id IN (
    SELECT id FROM posts WHERE status = 1  -- 1 = PUBLISHED
);

-- 验证结果
DO $$
DECLARE
    published_count INT;
    views_count INT;
    min_views INT;
    max_views INT;
    avg_views NUMERIC;
BEGIN
    -- 获取已发布文章数量
    SELECT COUNT(*) INTO published_count 
    FROM posts WHERE status = 1;
    
    -- 获取有浏览记录的文章数量
    SELECT COUNT(*) INTO views_count 
    FROM post_stats 
    WHERE post_id IN (SELECT id FROM posts WHERE status = 1)
    AND view_count > 0;
    
    -- 获取浏览数统计
    SELECT 
        MIN(view_count),
        MAX(view_count),
        ROUND(AVG(view_count), 2)
    INTO min_views, max_views, avg_views
    FROM post_stats
    WHERE post_id IN (SELECT id FROM posts WHERE status = 1);
    
    -- 输出结果
    RAISE NOTICE '已发布文章数量: %', published_count;
    RAISE NOTICE '有浏览记录的文章数量: %', views_count;
    RAISE NOTICE '浏览数范围: % - %', min_views, max_views;
    RAISE NOTICE '平均浏览数: %', avg_views;
    
    -- 验证
    IF views_count = published_count THEN
        RAISE NOTICE '✓ 所有已发布文章都有浏览记录';
    ELSE
        RAISE WARNING '部分已发布文章缺少浏览记录';
    END IF;
    
    IF min_views >= 10 AND max_views <= 100 THEN
        RAISE NOTICE '✓ 浏览数在预期范围内 (10-100)';
    ELSE
        RAISE WARNING '浏览数超出预期范围';
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
   psql -h localhost -p 5432 -U postgres -d ZhiCore -f generate-post-views.sql

2. 使用 PowerShell：
   $env:PGPASSWORD="postgres123456"
   psql -h localhost -p 5432 -U postgres -d ZhiCore -f generate-post-views.sql

前置条件：
- PostgreSQL 服务已启动
- ZhiCore 数据库已创建
- posts 表已存在且包含已发布文章
- post_stats 表已初始化（执行 init-post-stats.sql）

验证需求：
- Requirements 5.2: 为每篇已发布文章生成 10-100 次浏览记录
*/
