-- =====================================================
-- ZhiCore Test Data Generation - User Data
-- 博客测试数据生成 - 用户数据
-- 
-- 说明：此脚本用于生成测试用户数据
-- 执行方式：在 ZhiCore_user 数据库中执行
-- 
-- 生成数据：
-- - 3 个管理员用户
-- - 5 个审核员用户
-- - 50 个普通用户
-- 
-- 注意：
-- 1. 用户 ID 需要从 ID Generator 服务获取
-- 2. 此脚本仅生成用户基础数据和角色分配
-- 3. 密码使用 BCrypt 加密（密码为：Test@123456）
-- 4. 所有用户名以 test_ 开头，便于识别和清理
-- =====================================================

BEGIN;

-- =====================================================
-- 1. 清理旧的测试数据
-- =====================================================

-- 删除测试用户的角色关联
DELETE FROM user_roles 
WHERE user_id IN (
    SELECT id FROM users WHERE username LIKE 'test_%'
);

-- 删除测试用户的关注统计
DELETE FROM user_follow_stats 
WHERE user_id IN (
    SELECT id FROM users WHERE username LIKE 'test_%'
);

-- 删除测试用户的签到统计
DELETE FROM user_check_in_stats 
WHERE user_id IN (
    SELECT id FROM users WHERE username LIKE 'test_%'
);

-- 删除测试用户
DELETE FROM users WHERE username LIKE 'test_%';

-- =====================================================
-- 2. 生成用户数据
-- =====================================================

-- 注意：用户 ID 需要从 ZhiCore-id-generator 服务获取
-- 在实际执行时，需要先调用 ZhiCore-id-generator API 获取 58 个 ID
-- 然后替换下面的占位符 {ID_1}, {ID_2}, ... {ID_58}
--
-- API 端点：GET http://localhost:8088/api/v1/id/snowflake/batch?count=58

-- BCrypt 加密的密码（原始密码：Test@123456）
-- 使用 BCrypt 强度 10
-- $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

-- =====================================================
-- 2.1 生成管理员用户（3个）
-- =====================================================

INSERT INTO users (
    id, 
    username, 
    nick_name, 
    email, 
    email_confirmed,
    password_hash, 
    phone_number,
    phone_number_confirmed,
    avatar_url, 
    bio, 
    is_active,
    created_at,
    updated_at,
    deleted
) VALUES
-- 管理员 1
(
    {ID_1},
    'test_admin_001',
    '测试管理员001',
    'test_admin_001@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000001',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=admin001',
    '我是测试管理员001，负责系统管理和维护。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '90 days',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    false
),
-- 管理员 2
(
    {ID_2},
    'test_admin_002',
    '测试管理员002',
    'test_admin_002@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000002',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=admin002',
    '我是测试管理员002，负责用户管理和权限控制。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '85 days',
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    false
),
-- 管理员 3
(
    {ID_3},
    'test_admin_003',
    '测试管理员003',
    'test_admin_003@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000003',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=admin003',
    '我是测试管理员003，负责内容审核和数据分析。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '80 days',
    CURRENT_TIMESTAMP - INTERVAL '3 days',
    false
);

-- =====================================================
-- 2.2 生成审核员用户（5个）
-- =====================================================

INSERT INTO users (
    id, 
    username, 
    nick_name, 
    email, 
    email_confirmed,
    password_hash, 
    phone_number,
    phone_number_confirmed,
    avatar_url, 
    bio, 
    is_active,
    created_at,
    updated_at,
    deleted
) VALUES
-- 审核员 1
(
    {ID_4},
    'test_moderator_001',
    '测试审核员001',
    'test_moderator_001@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000004',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=moderator001',
    '我是测试审核员001，负责文章内容审核。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '75 days',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    false
),
-- 审核员 2
(
    {ID_5},
    'test_moderator_002',
    '测试审核员002',
    'test_moderator_002@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000005',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=moderator002',
    '我是测试审核员002，负责评论内容审核。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '70 days',
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    false
),
-- 审核员 3
(
    {ID_6},
    'test_moderator_003',
    '测试审核员003',
    'test_moderator_003@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000006',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=moderator003',
    '我是测试审核员003，负责用户举报处理。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '65 days',
    CURRENT_TIMESTAMP - INTERVAL '3 days',
    false
),
-- 审核员 4
(
    {ID_7},
    'test_moderator_004',
    '测试审核员004',
    'test_moderator_004@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000007',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=moderator004',
    '我是测试审核员004，负责违规内容处理。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '60 days',
    CURRENT_TIMESTAMP - INTERVAL '4 days',
    false
),
-- 审核员 5
(
    {ID_8},
    'test_moderator_005',
    '测试审核员005',
    'test_moderator_005@example.com',
    true,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '13800000008',
    true,
    'https://api.dicebear.com/7.x/avataaars/svg?seed=moderator005',
    '我是测试审核员005，负责敏感词过滤和内容质量把控。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '55 days',
    CURRENT_TIMESTAMP - INTERVAL '5 days',
    false
);

-- =====================================================
-- 2.3 生成普通用户（50个）
-- =====================================================

INSERT INTO users (
    id, 
    username, 
    nick_name, 
    email, 
    email_confirmed,
    password_hash, 
    phone_number,
    phone_number_confirmed,
    avatar_url, 
    bio, 
    is_active,
    created_at,
    updated_at,
    deleted
) VALUES
-- 普通用户 1-10
({ID_9}, 'test_user_001', '测试用户001', 'test_user_001@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000001', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user001', '热爱技术，喜欢分享编程经验。', true, CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', false),
({ID_10}, 'test_user_002', '测试用户002', 'test_user_002@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000002', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user002', '前端开发工程师，专注于 React 和 Vue。', true, CURRENT_TIMESTAMP - INTERVAL '48 days', CURRENT_TIMESTAMP - INTERVAL '2 hours', false),
({ID_11}, 'test_user_003', '测试用户003', 'test_user_003@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000003', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user003', '后端开发者，Java 和 Spring Boot 爱好者。', true, CURRENT_TIMESTAMP - INTERVAL '46 days', CURRENT_TIMESTAMP - INTERVAL '3 hours', false),
({ID_12}, 'test_user_004', '测试用户004', 'test_user_004@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000004', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user004', '全栈工程师，喜欢探索新技术。', true, CURRENT_TIMESTAMP - INTERVAL '44 days', CURRENT_TIMESTAMP - INTERVAL '4 hours', false),
({ID_13}, 'test_user_005', '测试用户005', 'test_user_005@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user005', 'DevOps 工程师，专注于容器化和自动化部署。', true, CURRENT_TIMESTAMP - INTERVAL '42 days', CURRENT_TIMESTAMP - INTERVAL '5 hours', false),
({ID_14}, 'test_user_006', '测试用户006', 'test_user_006@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000006', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user006', '数据分析师，热衷于数据可视化。', true, CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '6 hours', false),
({ID_15}, 'test_user_007', '测试用户007', 'test_user_007@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000007', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user007', '移动开发者，专注于 Flutter 和 React Native。', true, CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '7 hours', false),
({ID_16}, 'test_user_008', '测试用户008', 'test_user_008@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000008', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user008', '算法工程师，喜欢研究机器学习。', true, CURRENT_TIMESTAMP - INTERVAL '36 days', CURRENT_TIMESTAMP - INTERVAL '8 hours', false),
({ID_17}, 'test_user_009', '测试用户009', 'test_user_009@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000009', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user009', '产品经理，关注用户体验和产品设计。', true, CURRENT_TIMESTAMP - INTERVAL '34 days', CURRENT_TIMESTAMP - INTERVAL '9 hours', false),
({ID_18}, 'test_user_010', '测试用户010', 'test_user_010@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user010', 'UI/UX 设计师，追求极致的视觉体验。', true, CURRENT_TIMESTAMP - INTERVAL '32 days', CURRENT_TIMESTAMP - INTERVAL '10 hours', false),
-- 普通用户 11-20
({ID_19}, 'test_user_011', '测试用户011', 'test_user_011@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000011', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user011', '测试工程师，专注于自动化测试。', true, CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '11 hours', false),
({ID_20}, 'test_user_012', '测试用户012', 'test_user_012@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000012', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user012', '安全工程师，关注网络安全和渗透测试。', true, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '12 hours', false),
({ID_21}, 'test_user_013', '测试用户013', 'test_user_013@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000013', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user013', '架构师，专注于微服务和分布式系统。', true, CURRENT_TIMESTAMP - INTERVAL '26 days', CURRENT_TIMESTAMP - INTERVAL '13 hours', false),
({ID_22}, 'test_user_014', '测试用户014', 'test_user_014@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000014', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user014', '技术写作者，喜欢分享技术文章。', true, CURRENT_TIMESTAMP - INTERVAL '24 days', CURRENT_TIMESTAMP - INTERVAL '14 hours', false),
({ID_23}, 'test_user_015', '测试用户015', 'test_user_015@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user015', '学生，正在学习编程。', true, CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '15 hours', false),
({ID_24}, 'test_user_016', '测试用户016', 'test_user_016@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000016', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user016', '游戏开发者，热爱 Unity 和 Unreal。', true, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '16 hours', false),
({ID_25}, 'test_user_017', '测试用户017', 'test_user_017@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000017', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user017', '区块链开发者，研究智能合约。', true, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '17 hours', false),
({ID_26}, 'test_user_018', '测试用户018', 'test_user_018@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000018', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user018', '云计算工程师，专注于 AWS 和 Azure。', true, CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '18 hours', false),
({ID_27}, 'test_user_019', '测试用户019', 'test_user_019@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000019', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user019', '嵌入式开发者，喜欢 IoT 和硬件编程。', true, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '19 hours', false),
({ID_28}, 'test_user_020', '测试用户020', 'test_user_020@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user020', '技术博主，记录学习和成长。', false, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '20 hours', false),
-- 普通用户 21-30
({ID_29}, 'test_user_021', '测试用户021', 'test_user_021@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000021', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user021', '数据库管理员，精通 MySQL 和 PostgreSQL。', true, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', false),
({ID_30}, 'test_user_022', '测试用户022', 'test_user_022@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000022', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user022', '运维工程师，负责服务器维护。', true, CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '2 hours', false),
({ID_31}, 'test_user_023', '测试用户023', 'test_user_023@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000023', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user023', '性能优化专家，关注系统性能调优。', true, CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '3 hours', false),
({ID_32}, 'test_user_024', '测试用户024', 'test_user_024@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000024', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user024', 'Python 开发者，热爱数据科学。', true, CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '4 hours', false),
({ID_33}, 'test_user_025', '测试用户025', 'test_user_025@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user025', 'Go 语言爱好者，专注于高并发系统。', true, CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '5 hours', false),
({ID_34}, 'test_user_026', '测试用户026', 'test_user_026@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000026', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user026', 'Rust 开发者，追求极致性能。', true, CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '6 hours', false),
({ID_35}, 'test_user_027', '测试用户027', 'test_user_027@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000027', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user027', 'C++ 程序员，专注于系统编程。', true, CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '7 hours', false),
({ID_36}, 'test_user_028', '测试用户028', 'test_user_028@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000028', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user028', 'TypeScript 开发者，喜欢类型安全。', true, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '8 hours', false),
({ID_37}, 'test_user_029', '测试用户029', 'test_user_029@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000029', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user029', 'Kotlin 开发者，专注于 Android 开发。', true, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '9 hours', false),
({ID_38}, 'test_user_030', '测试用户030', 'test_user_030@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user030', 'Swift 开发者，热爱 iOS 开发。', false, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '10 hours', false),
-- 普通用户 31-40
({ID_39}, 'test_user_031', '测试用户031', 'test_user_031@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000031', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user031', 'PHP 开发者，专注于 Laravel 框架。', true, CURRENT_TIMESTAMP - INTERVAL '50 days', CURRENT_TIMESTAMP - INTERVAL '11 hours', false),
({ID_40}, 'test_user_032', '测试用户032', 'test_user_032@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000032', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user032', 'Ruby 开发者，喜欢 Rails 的优雅。', true, CURRENT_TIMESTAMP - INTERVAL '48 days', CURRENT_TIMESTAMP - INTERVAL '12 hours', false),
({ID_41}, 'test_user_033', '测试用户033', 'test_user_033@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000033', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user033', 'Scala 开发者，函数式编程爱好者。', true, CURRENT_TIMESTAMP - INTERVAL '46 days', CURRENT_TIMESTAMP - INTERVAL '13 hours', false),
({ID_42}, 'test_user_034', '测试用户034', 'test_user_034@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000034', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user034', 'Elixir 开发者，专注于并发编程。', true, CURRENT_TIMESTAMP - INTERVAL '44 days', CURRENT_TIMESTAMP - INTERVAL '14 hours', false),
({ID_43}, 'test_user_035', '测试用户035', 'test_user_035@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user035', 'Haskell 开发者，纯函数式编程实践者。', true, CURRENT_TIMESTAMP - INTERVAL '42 days', CURRENT_TIMESTAMP - INTERVAL '15 hours', false),
({ID_44}, 'test_user_036', '测试用户036', 'test_user_036@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000036', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user036', 'Clojure 开发者，Lisp 方言爱好者。', true, CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '16 hours', false),
({ID_45}, 'test_user_037', '测试用户037', 'test_user_037@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000037', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user037', 'Dart 开发者，Flutter 应用开发专家。', true, CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '17 hours', false),
({ID_46}, 'test_user_038', '测试用户038', 'test_user_038@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000038', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user038', 'Lua 开发者，游戏脚本编写者。', true, CURRENT_TIMESTAMP - INTERVAL '36 days', CURRENT_TIMESTAMP - INTERVAL '18 hours', false),
({ID_47}, 'test_user_039', '测试用户039', 'test_user_039@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000039', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user039', 'R 语言开发者，统计分析专家。', true, CURRENT_TIMESTAMP - INTERVAL '34 days', CURRENT_TIMESTAMP - INTERVAL '19 hours', false),
({ID_48}, 'test_user_040', '测试用户040', 'test_user_040@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user040', 'MATLAB 开发者，科学计算研究者。', true, CURRENT_TIMESTAMP - INTERVAL '32 days', CURRENT_TIMESTAMP - INTERVAL '20 hours', false),
-- 普通用户 41-50
({ID_49}, 'test_user_041', '测试用户041', 'test_user_041@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000041', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user041', 'Shell 脚本专家，自动化运维高手。', true, CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', false),
({ID_50}, 'test_user_042', '测试用户042', 'test_user_042@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000042', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user042', 'SQL 专家，数据库查询优化大师。', true, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '2 hours', false),
({ID_51}, 'test_user_043', '测试用户043', 'test_user_043@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000043', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user043', 'HTML/CSS 专家，前端样式大师。', true, CURRENT_TIMESTAMP - INTERVAL '26 days', CURRENT_TIMESTAMP - INTERVAL '3 hours', false),
({ID_52}, 'test_user_044', '测试用户044', 'test_user_044@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000044', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user044', 'JavaScript 全栈开发者，Node.js 专家。', true, CURRENT_TIMESTAMP - INTERVAL '24 days', CURRENT_TIMESTAMP - INTERVAL '4 hours', false),
({ID_53}, 'test_user_045', '测试用户045', 'test_user_045@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user045', 'GraphQL 开发者，API 设计专家。', true, CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '5 hours', false),
({ID_54}, 'test_user_046', '测试用户046', 'test_user_046@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000046', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user046', 'WebAssembly 开发者，高性能 Web 应用专家。', true, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '6 hours', false),
({ID_55}, 'test_user_047', '测试用户047', 'test_user_047@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000047', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user047', 'WebGL 开发者，3D 图形编程爱好者。', true, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '7 hours', false),
({ID_56}, 'test_user_048', '测试用户048', 'test_user_048@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000048', false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user048', 'WebRTC 开发者，实时通信专家。', true, CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '8 hours', false),
({ID_57}, 'test_user_049', '测试用户049', 'test_user_049@example.com', true, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '13900000049', true, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user049', 'PWA 开发者，渐进式 Web 应用专家。', true, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '9 hours', false),
({ID_58}, 'test_user_050', '测试用户050', 'test_user_050@example.com', false, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL, false, 'https://api.dicebear.com/7.x/avataaars/svg?seed=user050', '开源贡献者，热爱分享和协作。', false, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '10 hours', false);


-- =====================================================
-- 3. 分配用户角色
-- =====================================================

-- 3.1 为管理员分配 ADMIN 角色
INSERT INTO user_roles (user_id, role_id)
SELECT id, 1 FROM users WHERE username LIKE 'test_admin_%';

-- 3.2 为审核员分配 MODERATOR 角色
INSERT INTO user_roles (user_id, role_id)
SELECT id, 2 FROM users WHERE username LIKE 'test_moderator_%';

-- 3.3 为普通用户分配 USER 角色
INSERT INTO user_roles (user_id, role_id)
SELECT id, 3 FROM users WHERE username LIKE 'test_user_%';

-- =====================================================
-- 4. 初始化用户关注统计
-- =====================================================

-- 为所有测试用户创建关注统计记录
INSERT INTO user_follow_stats (user_id, followers_count, following_count)
SELECT id, 0, 0 FROM users WHERE username LIKE 'test_%';

-- =====================================================
-- 5. 初始化用户签到统计
-- =====================================================

-- 为所有测试用户创建签到统计记录
INSERT INTO user_check_in_stats (user_id, total_days, continuous_days, max_continuous_days, last_check_in_date)
SELECT id, 0, 0, 0, NULL FROM users WHERE username LIKE 'test_%';

-- =====================================================
-- 6. 数据验证
-- =====================================================

-- 验证用户数量
DO $$
DECLARE
    admin_count INT;
    moderator_count INT;
    regular_count INT;
    total_count INT;
BEGIN
    -- 统计各类用户数量
    SELECT COUNT(*) INTO admin_count FROM users WHERE username LIKE 'test_admin_%';
    SELECT COUNT(*) INTO moderator_count FROM users WHERE username LIKE 'test_moderator_%';
    SELECT COUNT(*) INTO regular_count FROM users WHERE username LIKE 'test_user_%';
    SELECT COUNT(*) INTO total_count FROM users WHERE username LIKE 'test_%';
    
    -- 验证数量
    IF admin_count < 3 THEN
        RAISE EXCEPTION '管理员用户数量不足：期望 3，实际 %', admin_count;
    END IF;
    
    IF moderator_count < 5 THEN
        RAISE EXCEPTION '审核员用户数量不足：期望 5，实际 %', moderator_count;
    END IF;
    
    IF regular_count < 50 THEN
        RAISE EXCEPTION '普通用户数量不足：期望 50，实际 %', regular_count;
    END IF;
    
    IF total_count < 58 THEN
        RAISE EXCEPTION '总用户数量不足：期望 58，实际 %', total_count;
    END IF;
    
    -- 输出统计信息
    RAISE NOTICE '✓ 用户数据生成成功';
    RAISE NOTICE '  - 管理员用户: %', admin_count;
    RAISE NOTICE '  - 审核员用户: %', moderator_count;
    RAISE NOTICE '  - 普通用户: %', regular_count;
    RAISE NOTICE '  - 总计: %', total_count;
END $$;

-- 验证用户名唯一性
DO $$
DECLARE
    duplicate_count INT;
BEGIN
    SELECT COUNT(*) - COUNT(DISTINCT username) INTO duplicate_count
    FROM users WHERE username LIKE 'test_%';
    
    IF duplicate_count > 0 THEN
        RAISE EXCEPTION '发现重复的用户名：% 个', duplicate_count;
    END IF;
    
    RAISE NOTICE '✓ 用户名唯一性验证通过';
END $$;

-- 验证邮箱唯一性
DO $$
DECLARE
    duplicate_count INT;
BEGIN
    SELECT COUNT(*) - COUNT(DISTINCT email) INTO duplicate_count
    FROM users WHERE username LIKE 'test_%' AND email IS NOT NULL;
    
    IF duplicate_count > 0 THEN
        RAISE EXCEPTION '发现重复的邮箱：% 个', duplicate_count;
    END IF;
    
    RAISE NOTICE '✓ 邮箱唯一性验证通过';
END $$;

-- 验证角色分配
DO $$
DECLARE
    admin_role_count INT;
    moderator_role_count INT;
    user_role_count INT;
BEGIN
    -- 统计角色分配数量
    SELECT COUNT(*) INTO admin_role_count 
    FROM user_roles ur
    JOIN users u ON ur.user_id = u.id
    WHERE u.username LIKE 'test_admin_%' AND ur.role_id = 1;
    
    SELECT COUNT(*) INTO moderator_role_count 
    FROM user_roles ur
    JOIN users u ON ur.user_id = u.id
    WHERE u.username LIKE 'test_moderator_%' AND ur.role_id = 2;
    
    SELECT COUNT(*) INTO user_role_count 
    FROM user_roles ur
    JOIN users u ON ur.user_id = u.id
    WHERE u.username LIKE 'test_user_%' AND ur.role_id = 3;
    
    -- 验证角色分配
    IF admin_role_count < 3 THEN
        RAISE EXCEPTION '管理员角色分配不足：期望 3，实际 %', admin_role_count;
    END IF;
    
    IF moderator_role_count < 5 THEN
        RAISE EXCEPTION '审核员角色分配不足：期望 5，实际 %', moderator_role_count;
    END IF;
    
    IF user_role_count < 50 THEN
        RAISE EXCEPTION '普通用户角色分配不足：期望 50，实际 %', user_role_count;
    END IF;
    
    RAISE NOTICE '✓ 角色分配验证通过';
    RAISE NOTICE '  - ADMIN 角色: %', admin_role_count;
    RAISE NOTICE '  - MODERATOR 角色: %', moderator_role_count;
    RAISE NOTICE '  - USER 角色: %', user_role_count;
END $$;

-- 验证激活状态
DO $$
DECLARE
    active_count INT;
    inactive_count INT;
    active_ratio NUMERIC;
BEGIN
    SELECT COUNT(*) INTO active_count 
    FROM users WHERE username LIKE 'test_%' AND is_active = true;
    
    SELECT COUNT(*) INTO inactive_count 
    FROM users WHERE username LIKE 'test_%' AND is_active = false;
    
    active_ratio := active_count::NUMERIC / (active_count + inactive_count);
    
    IF active_ratio < 0.8 THEN
        RAISE WARNING '激活用户比例偏低：%.2f%%（期望 >= 80%%）', active_ratio * 100;
    END IF;
    
    RAISE NOTICE '✓ 用户激活状态验证完成';
    RAISE NOTICE '  - 激活用户: %', active_count;
    RAISE NOTICE '  - 未激活用户: %', inactive_count;
    RAISE NOTICE '  - 激活比例: %.2f%%', active_ratio * 100;
END $$;

COMMIT;

-- =====================================================
-- 数据生成完成
-- =====================================================

-- 输出最终统计
SELECT 
    '用户数据生成完成' AS status,
    COUNT(*) AS total_users,
    COUNT(*) FILTER (WHERE username LIKE 'test_admin_%') AS admin_users,
    COUNT(*) FILTER (WHERE username LIKE 'test_moderator_%') AS moderator_users,
    COUNT(*) FILTER (WHERE username LIKE 'test_user_%') AS regular_users,
    COUNT(*) FILTER (WHERE is_active = true) AS active_users,
    COUNT(*) FILTER (WHERE is_active = false) AS inactive_users
FROM users 
WHERE username LIKE 'test_%';

-- =====================================================
-- 使用说明
-- =====================================================

-- 1. 执行前准备：
--    - 确保 ZhiCore_user 数据库已创建并初始化
--    - 确保 ZhiCore-id-generator 服务正常运行（http://localhost:8088）
--    - 使用 PowerShell 脚本获取 58 个用户 ID
--
-- 2. 获取 ID 示例（PowerShell）：
--    $response = Invoke-RestMethod -Uri "http://localhost:8088/api/v1/id/snowflake/batch?count=58" -Method Get
--    $ids = $response.data
--
-- 3. 替换占位符：
--    - 将脚本中的 {ID_1}, {ID_2}, ... {ID_58} 替换为实际的 ID
--    - 可以使用文本编辑器的查找替换功能
--
-- 4. 执行脚本：
--    psql -U postgres -d ZhiCore_user -f generate-users.sql
--
-- 5. 验证结果：
--    SELECT * FROM users WHERE username LIKE 'test_%' ORDER BY username;
--    SELECT u.username, r.name FROM users u 
--    JOIN user_roles ur ON u.id = ur.user_id 
--    JOIN roles r ON ur.role_id = r.id 
--    WHERE u.username LIKE 'test_%' 
--    ORDER BY u.username;
--
-- 6. 清理测试数据：
--    DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
--    DELETE FROM user_follow_stats WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
--    DELETE FROM user_check_in_stats WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
--    DELETE FROM users WHERE username LIKE 'test_%';
--
-- 7. 默认密码：
--    所有测试用户的密码都是：Test@123456
--    密码哈希：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
--
-- =====================================================
