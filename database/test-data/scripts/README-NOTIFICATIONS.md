# 通知数据生成说明

## 概述

本文档说明如何生成测试通知数据。通知数据包括点赞、评论、回复、关注和系统通知等多种类型。

## 前置条件

1. **服务运行**：
   - ZhiCore-id-generator 服务已启动（端口 8088）
   - PostgreSQL 数据库已启动（端口 5432）

2. **基础数据**：
   - 已执行用户数据生成脚本（必需）
   - 已执行文章数据生成脚本（必需）
   - 已执行评论数据生成脚本（可选，影响评论相关通知）
   - 已执行关注关系生成脚本（可选，影响关注通知）

## 通知类型

脚本会生成以下类型的通知：

| 类型 | 值 | 说明 | 需要的数据 |
|------|---|------|-----------|
| LIKE | 0 | 点赞通知 | 文章或评论 |
| COMMENT | 1 | 评论通知 | 文章 |
| REPLY | 2 | 回复通知 | 评论 |
| FOLLOW | 3 | 关注通知 | 关注关系 |
| SYSTEM | 4 | 系统通知 | 无 |

## 使用方法

### 1. 基本使用

使用默认配置生成通知数据：

```powershell
.\Generate-Notifications.ps1
```

这将：
- 为每个用户生成 10-50 条通知
- 50% 的通知标记为已读
- 生成 SQL 文件到 `../sql/generated-notifications.sql`

### 2. 预览模式

查看将要生成的数据但不创建 SQL 文件：

```powershell
.\Generate-Notifications.ps1 -DryRun
```

### 3. 自定义参数

指定每个用户的通知数量和已读比例：

```powershell
.\Generate-Notifications.ps1 -MinNotificationsPerUser 20 -MaxNotificationsPerUser 100 -ReadRatio 0.6
```

参数说明：
- `MinNotificationsPerUser`: 每个用户最少通知数（默认 10）
- `MaxNotificationsPerUser`: 每个用户最多通知数（默认 50）
- `ReadRatio`: 已读通知比例，0.0-1.0（默认 0.5）

### 4. 使用配置文件

使用自定义配置文件：

```powershell
.\Generate-Notifications.ps1 -ConfigPath ".\custom-config.json"
```

配置文件格式：

```json
{
  "idGeneratorUrl": "http://localhost:8088",
  "notifications": {
    "perUser": {
      "min": 10,
      "max": 50
    },
    "readRatio": 0.5
  }
}
```

## 执行 SQL 文件

脚本会生成 SQL 文件但不会自动执行。需要手动执行 SQL 文件插入数据：

```powershell
# Windows (PowerShell)
$env:PGPASSWORD = "postgres123456"
psql -h localhost -p 5432 -U postgres -f "..\sql\generated-notifications.sql"
```

或者使用 Execute-SqlFile.ps1 脚本（如果存在）：

```powershell
.\Execute-SqlFile.ps1 -FilePath "..\sql\generated-notifications.sql"
```

## 数据验证

### 验证通知数量

```sql
-- 连接到 ZhiCore_notification 数据库
\c ZhiCore_notification;

-- 查询总通知数
SELECT COUNT(*) as total_notifications FROM notifications;

-- 查询每个用户的通知数
SELECT recipient_id, COUNT(*) as notification_count
FROM notifications
GROUP BY recipient_id
ORDER BY notification_count DESC;
```

### 验证通知类型分布

```sql
-- 查询各类型通知数量
SELECT 
    CASE type
        WHEN 0 THEN 'LIKE'
        WHEN 1 THEN 'COMMENT'
        WHEN 2 THEN 'REPLY'
        WHEN 3 THEN 'FOLLOW'
        WHEN 4 THEN 'SYSTEM'
    END as notification_type,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM notifications), 2) as percentage
FROM notifications
GROUP BY type
ORDER BY type;
```

### 验证已读状态

```sql
-- 查询已读/未读统计
SELECT 
    is_read,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM notifications), 2) as percentage
FROM notifications
GROUP BY is_read;
```

### 验证数据完整性

```sql
-- 验证 actor_id 有效性（排除系统通知）
SELECT COUNT(*) as invalid_actor_count
FROM notifications n
WHERE n.type != 4  -- 不是系统通知
  AND n.actor_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.id = n.actor_id
  );

-- 验证 target_id 有效性（点赞和评论通知）
SELECT COUNT(*) as invalid_target_count
FROM notifications n
WHERE n.target_type = 'post'
  AND n.target_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM posts p WHERE p.id = n.target_id
  );

-- 验证已读通知有 read_at 时间戳
SELECT COUNT(*) as invalid_read_at_count
FROM notifications
WHERE is_read = TRUE AND read_at IS NULL;
```

## 生成的数据特征

### 通知分布

- **点赞通知**：针对用户的文章或评论
- **评论通知**：针对用户的文章
- **回复通知**：针对用户的评论
- **关注通知**：基于实际的关注关系
- **系统通知**：随机系统消息

### 数据关系

- `recipient_id`: 接收通知的用户 ID（必需）
- `actor_id`: 触发通知的用户 ID（系统通知除外）
- `target_type`: 目标类型（post/comment，关注和系统通知为 NULL）
- `target_id`: 目标 ID（关注和系统通知为 NULL）
- `is_read`: 是否已读（根据 ReadRatio 随机设置）
- `read_at`: 已读时间（仅当 is_read 为 true 时设置）

### 内容模板

系统通知使用以下模板：
- "欢迎使用博客系统！"
- "你的文章已通过审核"
- "系统将于今晚进行维护"
- "恭喜你获得优秀作者称号！"
- "你的评论收到了很多赞"
- "系统功能更新通知"
- "你的文章被推荐到首页"
- "账号安全提醒"
- "新功能上线通知"
- "活动邀请通知"

## 常见问题

### 1. 脚本报错 "未找到测试用户"

**原因**：未执行用户数据生成脚本

**解决方案**：
```powershell
.\Execute-UserGeneration.ps1
```

### 2. 脚本报错 "未找到已发布文章"

**原因**：未执行文章数据生成脚本

**解决方案**：
```powershell
.\Generate-Posts.ps1
```

### 3. 生成的通知类型不均衡

**原因**：缺少某些基础数据（如评论、关注关系）

**解决方案**：
- 执行评论生成脚本：`.\Generate-Comments.ps1`
- 执行关注关系生成脚本：`.\Generate-UserFollows.ps1`

### 4. SQL 文件执行失败

**原因**：可能是数据库连接问题或权限问题

**解决方案**：
```powershell
# 检查数据库连接
$env:PGPASSWORD = "postgres123456"
psql -h localhost -p 5432 -U postgres -d ZhiCore_notification -c "SELECT 1"

# 检查表是否存在
psql -h localhost -p 5432 -U postgres -d ZhiCore_notification -c "\dt"
```

### 5. ID Generator 服务不可用

**原因**：服务未启动或端口被占用

**解决方案**：
```powershell
# 检查服务状态
Test-NetConnection -ComputerName localhost -Port 8088

# 启动服务（根据实际情况）
cd ZhiCore-microservice
.\scripts\start-all-services.ps1
```

## 性能考虑

### 生成时间

- 50 个用户，每用户 30 条通知：约 1-2 分钟
- 主要时间消耗在 ID 生成和数据处理

### 优化建议

1. **批量 ID 生成**：如果需要生成大量通知，可以修改脚本使用批量 ID 生成
2. **并行处理**：对于大量用户，可以考虑并行处理
3. **数据库索引**：确保数据库有适当的索引

## 高粉作者联调建议

用于验证发文广播、免打扰和摘要补发：

1. 准备一个测试作者（例如 `author_id=90001`），批量写入至少 `10000` 条粉丝关系。
2. 发布一篇作品，确认 `notification_campaign` 和 `notification_campaign_shard` 有新记录。
3. 对粉丝分组配置偏好：
   - 一组开启站内+实时推送（PRIORITY）。
   - 一组仅站内（NORMAL）。
   - 一组作者订阅 `DIGEST_ONLY`（DIGEST）。
   - 一组作者订阅 `MUTED`（MUTED）。
4. 配置内容类免打扰窗口，验证命中窗口时不即时推送，转为 digest。

建议重点观察：

1. `notification_campaign_shard.status` 是否按 `PLANNED -> COMPLETED` 推进。
2. `notification_delivery.delivery_status` 的分布（如 `INBOX_CREATED`、`DIGEST_PENDING`、`SKIPPED`）。
3. 通知服务日志中的广播事件 `eventId/campaignId` 链路。
4. 未读数接口与 `notifications` 表未读条数是否一致。

## 清理数据

如果需要清理生成的通知数据：

```sql
-- 连接到 ZhiCore_notification 数据库
\c ZhiCore_notification;

-- 删除所有通知
BEGIN;
DELETE FROM notifications;
COMMIT;
```

或者只删除特定用户的通知：

```sql
-- 删除测试用户的通知
BEGIN;
DELETE FROM notifications
WHERE recipient_id IN (
    SELECT id FROM users WHERE username LIKE 'test_%'
);
COMMIT;
```

## 相关文档

- [测试数据生成总览](../README.md)
- [用户数据生成说明](./README-USERS.md)
- [文章数据生成说明](./README-POSTS.md)
- [评论数据生成说明](./README-COMMENTS.md)
- [关注关系生成说明](./README-FOLLOWS.md)

## 需求验证

此脚本验证以下需求：

- ✅ Requirements 8.1: 为每个用户生成 10-50 条通知
- ✅ Requirements 8.2: 生成不同类型的通知（点赞、评论、关注等）
- ✅ Requirements 8.3: 确保 recipient_id 是有效的用户 ID
- ✅ Requirements 8.4: 确保 actor_id 是有效的用户 ID（如果适用）
- ✅ Requirements 8.5: 确保 target_id 指向有效的目标对象
- ✅ Requirements 8.6: 为至少 50% 的通知设置 is_read 为 true
- ✅ Requirements 8.7: 当通知已读时，设置 read_at 时间戳
