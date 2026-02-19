# 系统消息数据生成总览

## 概述

本文档提供系统消息数据生成的总览，包括全局公告和小助手消息两个部分。这些脚本用于生成测试环境中的系统级消息数据。

## 组成部分

### 1. 全局公告 (Global Announcements)

全局公告是面向所有用户的系统级通知，用于发布重要信息、维护通知、活动公告等。

**脚本**: `Generate-Announcements.ps1`

**功能**:
- 生成不同类型的公告（INFO、WARNING、IMPORTANT、MAINTENANCE）
- 设置公告启用状态
- 为部分公告设置过期时间
- 分配管理员作为创建者

**详细文档**: [README-ANNOUNCEMENTS.md](./README-ANNOUNCEMENTS.md)

### 2. 小助手消息 (Assistant Messages)

小助手消息是发送给单个用户的个性化消息，用于引导用户、提供提示、通知成就等。

**脚本**: `Generate-AssistantMessages.ps1`

**功能**:
- 为每个用户生成个性化消息
- 支持多种消息类型（welcome、tip、achievement、reminder、update、activity、security）
- 第一条消息始终是欢迎消息
- 设置部分消息为已读状态

**详细文档**: [README-ASSISTANT-MESSAGES.md](./README-ASSISTANT-MESSAGES.md)

## 快速开始

### 前置条件

1. 启动必需的服务：
   ```powershell
   # 启动 ID Generator 服务（端口 8088）
   # 启动 PostgreSQL 数据库（端口 5432）
   ```

2. 确保已生成基础数据：
   ```powershell
   # 生成用户数据
   .\Execute-UserGeneration.ps1
   ```

### 生成全局公告

```powershell
# 进入脚本目录
cd blog-microservice/database/test-data/scripts

# 生成公告数据
.\Generate-Announcements.ps1

# 执行 SQL 文件
psql -h localhost -p 5432 -U postgres -f "..\sql\generated-announcements.sql"
```

### 生成小助手消息

```powershell
# 进入脚本目录
cd blog-microservice/database/test-data/scripts

# 生成小助手消息数据
.\Generate-AssistantMessages.ps1

# 执行 SQL 文件
psql -h localhost -p 5432 -U postgres -f "..\sql\generated-assistant-messages.sql"
```

## 执行顺序

建议按以下顺序执行系统消息数据生成：

1. **全局公告** - 可以独立执行，只依赖用户数据
2. **小助手消息** - 可以独立执行，只依赖用户数据

这两个脚本可以并行执行，互不依赖。

## 数据统计

### 默认配置下的数据量

假设有 58 个测试用户（3 个管理员 + 5 个审核员 + 50 个普通用户）：

| 数据类型 | 数量 | 说明 |
|---------|------|------|
| 全局公告 | 5 条 | 面向所有用户 |
| 小助手消息 | 约 377 条 | 每用户 3-10 条，平均 6.5 条 |
| **总计** | **约 382 条** | 系统消息总数 |

### 数据分布

**全局公告类型分布**:
- INFO: 约 40%
- WARNING: 约 20%
- IMPORTANT: 约 20%
- MAINTENANCE: 约 20%

**小助手消息类型分布**:
- welcome: 约 15% (每用户 1 条)
- tip: 约 15%
- achievement: 约 14%
- reminder: 约 14%
- update: 约 17%
- activity: 约 13%
- security: 约 12%

## 配置说明

### 全局配置文件

在 `test-data-config.json` 中配置：

```json
{
  "idGeneratorUrl": "http://localhost:8088",
  "announcements": {
    "count": 5,
    "enabledRatio": 0.6
  }
}
```

### 命令行参数

两个脚本都支持命令行参数覆盖配置：

```powershell
# 全局公告
.\Generate-Announcements.ps1 `
    -AnnouncementCount 10 `
    -EnabledRatio 0.8 `
    -IdGeneratorUrl "http://localhost:8088"

# 小助手消息
.\Generate-AssistantMessages.ps1 `
    -MinMessagesPerUser 5 `
    -MaxMessagesPerUser 15 `
    -ReadRatio 0.8 `
    -IdGeneratorUrl "http://localhost:8088"
```

## 数据验证

### 验证全局公告

```sql
\c blog_notification;

-- 查询公告总数
SELECT COUNT(*) FROM global_announcements;

-- 查询启用的公告
SELECT COUNT(*) FROM global_announcements WHERE is_enabled = TRUE;

-- 查询各类型公告数量
SELECT 
    CASE type
        WHEN 0 THEN 'INFO'
        WHEN 1 THEN 'WARNING'
        WHEN 2 THEN 'IMPORTANT'
        WHEN 3 THEN 'MAINTENANCE'
    END AS type_name,
    COUNT(*) as count
FROM global_announcements
GROUP BY type;
```

### 验证小助手消息

```sql
\c blog_notification;

-- 查询消息总数
SELECT COUNT(*) FROM assistant_messages;

-- 查询每个用户的消息数
SELECT user_id, COUNT(*) as message_count
FROM assistant_messages
GROUP BY user_id
ORDER BY message_count DESC
LIMIT 10;

-- 查询各类型消息数量
SELECT type, COUNT(*) as count
FROM assistant_messages
GROUP BY type
ORDER BY count DESC;

-- 验证每个用户都有欢迎消息
SELECT COUNT(DISTINCT user_id) as users_with_welcome
FROM assistant_messages
WHERE type = 'welcome';
```

## 故障排查

### 常见问题

1. **ID Generator 服务不可用**
   - 检查服务是否启动：`http://localhost:8088/api/v1/id/snowflake`
   - 检查端口是否被占用
   - 查看服务日志

2. **数据库连接失败**
   - 检查 PostgreSQL 是否运行
   - 验证连接参数（主机、端口、用户名、密码）
   - 检查数据库是否存在

3. **未找到用户数据**
   - 确认已执行用户数据生成脚本
   - 检查数据库中的用户数据

4. **SQL 执行失败**
   - 检查是否有重复数据
   - 清理旧数据后重新执行
   - 检查数据库权限

### 清理数据

如果需要重新生成数据，先清理旧数据：

```sql
\c blog_notification;

-- 清理全局公告
DELETE FROM global_announcements;

-- 清理小助手消息
DELETE FROM assistant_messages;
```

## 需求验证

### 全局公告需求

- ✅ **Requirements 9.1**: 生成至少 5 条全局公告
- ✅ **Requirements 9.2**: 确保至少 3 条公告处于启用状态
- ✅ **Requirements 9.3**: 为部分公告设置过期时间

### 小助手消息需求

- ✅ **Requirements 9.4**: 为每个用户生成 3-10 条小助手消息
- ✅ **Requirements 9.5**: 生成不同类型的消息
- ✅ **Requirements 9.6**: 为至少 60% 的小助手消息设置 is_read 为 true

## 相关文档

- [测试数据生成总览](../README.md)
- [全局公告生成详细文档](./README-ANNOUNCEMENTS.md)
- [小助手消息生成详细文档](./README-ASSISTANT-MESSAGES.md)
- [通知数据生成](./README-NOTIFICATIONS.md)
- [数据库表结构](../../docker/postgres-init/02-init-tables.sql)

## 维护说明

### 添加新的公告类型

在 `Generate-Announcements.ps1` 中修改 `$AnnouncementTypes` 和 `$AnnouncementTemplates`。

### 添加新的消息类型

在 `Generate-AssistantMessages.ps1` 中修改 `$MessageTemplates`。

### 修改数据量

通过配置文件或命令行参数调整：
- 公告数量：`AnnouncementCount`
- 每用户消息数：`MinMessagesPerUser` 和 `MaxMessagesPerUser`

### 修改已读比例

通过命令行参数调整：
- 公告启用比例：`EnabledRatio`
- 消息已读比例：`ReadRatio`

## 最后更新

- **日期**: 2026-02-13
- **版本**: 1.0.0
- **维护者**: 开发团队
