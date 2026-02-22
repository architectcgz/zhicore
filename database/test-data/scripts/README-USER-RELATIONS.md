# 用户关系数据生成脚本

## 概述

本目录包含用于生成测试用户关系数据的脚本，包括关注关系和拉黑关系。

## 脚本列表

### 1. Generate-UserFollows.ps1
生成用户关注关系数据

**功能**：
- 为每个用户生成 5-20 个关注关系
- 确保用户不关注自己
- 自动更新 user_follow_stats 统计

**详细文档**：[README-USER-FOLLOWS.md](./README-USER-FOLLOWS.md)

### 2. Generate-UserBlocks.ps1
生成用户拉黑关系数据

**功能**：
- 为部分用户（默认 30%）生成 0-5 个拉黑关系
- 确保用户不拉黑自己
- 模拟真实的拉黑场景

**详细文档**：[README-USER-BLOCKS.md](./README-USER-BLOCKS.md)

## 执行顺序

建议按以下顺序执行脚本：

```powershell
# 1. 生成用户关注关系
.\Generate-UserFollows.ps1

# 2. 生成用户拉黑关系
.\Generate-UserBlocks.ps1
```

## 快速开始

### 使用默认配置

```powershell
# 进入脚本目录
cd ZhiCore-microservice/database/test-data/scripts

# 生成关注关系
.\Generate-UserFollows.ps1

# 生成拉黑关系
.\Generate-UserBlocks.ps1
```

### 预览模式

```powershell
# 预览关注关系（不实际创建）
.\Generate-UserFollows.ps1 -DryRun

# 预览拉黑关系（不实际创建）
.\Generate-UserBlocks.ps1 -DryRun
```

### 使用配置文件

```powershell
# 使用自定义配置文件
.\Generate-UserFollows.ps1 -ConfigPath "..\custom-config.json"
.\Generate-UserBlocks.ps1 -ConfigPath "..\custom-config.json"
```

## 配置说明

### 配置文件格式

```json
{
  "apiBaseUrl": "http://localhost:8000",
  "appId": "test-app",
  "userRelations": {
    "followsPerUser": {
      "min": 5,
      "max": 20
    },
    "blocksPerUser": {
      "min": 0,
      "max": 5
    }
  }
}
```

### 配置参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| apiBaseUrl | API 基础地址 | http://localhost:8000 |
| appId | 应用 ID | test-app |
| userRelations.followsPerUser.min | 每用户最小关注数 | 5 |
| userRelations.followsPerUser.max | 每用户最大关注数 | 20 |
| userRelations.blocksPerUser.min | 每用户最小拉黑数 | 0 |
| userRelations.blocksPerUser.max | 每用户最大拉黑数 | 5 |

## 数据库表结构

### user_follows 表

```sql
CREATE TABLE user_follows (
    follower_id BIGINT NOT NULL,      -- 关注者 ID
    following_id BIGINT NOT NULL,     -- 被关注者 ID
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id)
);
```

### user_follow_stats 表

```sql
CREATE TABLE user_follow_stats (
    user_id BIGINT PRIMARY KEY,       -- 用户 ID
    followers_count INT NOT NULL DEFAULT 0,   -- 粉丝数
    following_count INT NOT NULL DEFAULT 0    -- 关注数
);
```

### user_blocks 表

```sql
CREATE TABLE user_blocks (
    blocker_id BIGINT NOT NULL,       -- 拉黑者 ID
    blocked_id BIGINT NOT NULL,       -- 被拉黑者 ID
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id)
);
```

## 生成数据统计

### 关注关系

假设有 58 个测试用户，每用户关注 5-20 个其他用户：

- **总关注关系数**：约 580-1160 个
- **平均每用户关注数**：约 10-20 个
- **平均每用户粉丝数**：约 10-20 个
- **覆盖率**：100% 用户

### 拉黑关系

假设有 58 个测试用户，30% 的用户有拉黑行为，每个拉黑用户拉黑 0-5 个其他用户：

- **有拉黑行为的用户数**：约 17 个
- **总拉黑关系数**：约 0-85 个
- **平均每个拉黑用户拉黑数**：约 0-5 个
- **覆盖率**：30% 用户

## 数据验证

### 完整性验证

```sql
-- 验证关注关系
-- 1. 用户不关注自己
SELECT COUNT(*) FROM user_follows WHERE follower_id = following_id;
-- 应该返回 0

-- 2. 所有 follower_id 都是有效用户
SELECT COUNT(*) 
FROM user_follows uf
LEFT JOIN users u ON uf.follower_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0

-- 3. 所有 following_id 都是有效用户
SELECT COUNT(*) 
FROM user_follows uf
LEFT JOIN users u ON uf.following_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0

-- 验证拉黑关系
-- 1. 用户不拉黑自己
SELECT COUNT(*) FROM user_blocks WHERE blocker_id = blocked_id;
-- 应该返回 0

-- 2. 所有 blocker_id 都是有效用户
SELECT COUNT(*) 
FROM user_blocks ub
LEFT JOIN users u ON ub.blocker_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0

-- 3. 所有 blocked_id 都是有效用户
SELECT COUNT(*) 
FROM user_blocks ub
LEFT JOIN users u ON ub.blocked_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0
```

### 统计一致性验证

```sql
-- 验证 following_count 一致性
SELECT 
    u.id,
    u.username,
    ufs.following_count as stats_count,
    COUNT(uf.following_id) as actual_count
FROM users u
LEFT JOIN user_follow_stats ufs ON u.id = ufs.user_id
LEFT JOIN user_follows uf ON u.id = uf.follower_id
WHERE u.username LIKE 'test_%'
GROUP BY u.id, u.username, ufs.following_count
HAVING ufs.following_count != COUNT(uf.following_id);
-- 应该返回空结果

-- 验证 followers_count 一致性
SELECT 
    u.id,
    u.username,
    ufs.followers_count as stats_count,
    COUNT(uf.follower_id) as actual_count
FROM users u
LEFT JOIN user_follow_stats ufs ON u.id = ufs.user_id
LEFT JOIN user_follows uf ON u.id = uf.following_id
WHERE u.username LIKE 'test_%'
GROUP BY u.id, u.username, ufs.followers_count
HAVING ufs.followers_count != COUNT(uf.follower_id);
-- 应该返回空结果
```

### 数据分布验证

```sql
-- 关注关系分布
SELECT 
    '关注数分布' as metric,
    MIN(following_count) as min_value,
    MAX(following_count) as max_value,
    AVG(following_count)::numeric(10,2) as avg_value,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY following_count) as median_value
FROM user_follow_stats
WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');

-- 粉丝数分布
SELECT 
    '粉丝数分布' as metric,
    MIN(followers_count) as min_value,
    MAX(followers_count) as max_value,
    AVG(followers_count)::numeric(10,2) as avg_value,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY followers_count) as median_value
FROM user_follow_stats
WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');

-- 拉黑关系分布
SELECT 
    '拉黑数分布' as metric,
    MIN(block_count) as min_value,
    MAX(block_count) as max_value,
    AVG(block_count)::numeric(10,2) as avg_value
FROM (
    SELECT blocker_id, COUNT(*) as block_count
    FROM user_blocks
    GROUP BY blocker_id
) sub;
```

## 常见问题

### Q1: 为什么有些用户没有拉黑关系？

**A**: 这是正常的。拉黑关系的生成策略是：
- 只有部分用户（默认 30%）有拉黑行为
- 每个拉黑用户的拉黑数量是随机的（0-5 个）
- 这更符合真实场景，因为大多数用户不会拉黑其他用户

### Q2: 如何增加拉黑关系的数量？

**A**: 可以通过以下方式：
```powershell
# 方式 1: 增加有拉黑行为的用户比例
.\Generate-UserBlocks.ps1 -BlockRatio 0.5

# 方式 2: 增加每用户的拉黑数量
.\Generate-UserBlocks.ps1 -MinBlocksPerUser 1 -MaxBlocksPerUser 10

# 方式 3: 同时调整两个参数
.\Generate-UserBlocks.ps1 -BlockRatio 0.5 -MinBlocksPerUser 2 -MaxBlocksPerUser 8
```

### Q3: 关注关系和拉黑关系可以同时存在吗？

**A**: 可以。在当前实现中：
- A 可以同时关注和拉黑 B
- 这取决于业务逻辑的具体要求
- 如果需要互斥，可以在 API 层面添加验证

### Q4: 如何清理已生成的关系数据？

**A**: 可以使用 SQL 脚本清理：
```sql
-- 清理测试用户的关注关系
DELETE FROM user_follows 
WHERE follower_id IN (SELECT id FROM users WHERE username LIKE 'test_%')
   OR following_id IN (SELECT id FROM users WHERE username LIKE 'test_%');

-- 清理测试用户的拉黑关系
DELETE FROM user_blocks 
WHERE blocker_id IN (SELECT id FROM users WHERE username LIKE 'test_%')
   OR blocked_id IN (SELECT id FROM users WHERE username LIKE 'test_%');

-- 重置关注统计
UPDATE user_follow_stats 
SET followers_count = 0, following_count = 0
WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
```

### Q5: 脚本执行失败怎么办？

**A**: 按以下步骤排查：
1. 检查服务是否启动（ZhiCore-gateway 端口 8000）
2. 验证测试用户是否存在
3. 查看详细错误信息
4. 使用 -DryRun 参数预览数据
5. 检查网络连接

## 性能考虑

### 执行时间

- **关注关系生成**：约 1-3 分钟（58 个用户，约 700 个关系）
- **拉黑关系生成**：约 10-30 秒（58 个用户，约 40 个关系）

### 优化建议

1. **批量创建**：如果关系数量很大，考虑实现批量创建 API
2. **并发执行**：可以使用 PowerShell 的并行功能加速
3. **数据库索引**：确保外键字段有索引

## 相关文档

- [用户关注关系详细文档](./README-USER-FOLLOWS.md)
- [用户拉黑关系详细文档](./README-USER-BLOCKS.md)
- [API 辅助模块文档](./modules/README.md)
- [测试数据生成总览](../README.md)

## 需求验证

这些脚本验证以下需求：

### 关注关系
- **Requirements 2.1**: 为每个用户生成 5-20 个关注关系
- **Requirements 2.2**: 确保 follower_id 和 following_id 都是有效的用户 ID
- **Requirements 2.3**: 更新 user_follow_stats 表的统计数据
- **Requirements 2.6**: 确保用户不会关注自己

### 拉黑关系
- **Requirements 2.4**: 为部分用户生成 1-5 个拉黑关系
- **Requirements 2.5**: 确保 blocker_id 和 blocked_id 都是有效的用户 ID
- **Requirements 2.6**: 确保用户不会拉黑自己

## 维护

- **最后更新**：2026-02-13
- **维护者**：开发团队
- **更新频率**：根据需求变化更新
