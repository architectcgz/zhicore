# 小助手消息数据生成脚本

## 概述

`Generate-AssistantMessages.ps1` 脚本用于生成测试小助手消息数据。该脚本为每个用户生成个性化的小助手消息，包括欢迎消息、提示、成就通知、提醒等多种类型。

## 功能特性

- ✅ 为每个用户生成小助手消息
- ✅ 支持多种消息类型（welcome、tip、achievement、reminder、update、activity、security）
- ✅ 第一条消息始终是欢迎消息
- ✅ 设置部分消息为已读状态
- ✅ 支持消息链接
- ✅ 生成 SQL 文件用于数据插入
- ✅ 支持配置文件和命令行参数
- ✅ 提供 Dry Run 模式预览数据

## 前置条件

1. **服务运行**：
   - blog-id-generator 服务已启动（端口 8088）
   - PostgreSQL 数据库已启动（端口 5432）

2. **数据依赖**：
   - 已执行用户数据生成脚本

3. **环境要求**：
   - PowerShell 5.1 或更高版本
   - psql 命令行工具（PostgreSQL 客户端）

## 使用方法

### 基本用法

```powershell
# 使用默认配置生成小助手消息数据
.\Generate-AssistantMessages.ps1

# 预览模式（不创建 SQL 文件）
.\Generate-AssistantMessages.ps1 -DryRun

# 指定每用户消息数量范围
.\Generate-AssistantMessages.ps1 -MinMessagesPerUser 5 -MaxMessagesPerUser 15

# 指定已读比例
.\Generate-AssistantMessages.ps1 -ReadRatio 0.8

# 使用自定义配置文件
.\Generate-AssistantMessages.ps1 -ConfigPath ".\custom-config.json"
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ConfigPath` | string | "" | 配置文件路径（可选） |
| `IdGeneratorUrl` | string | "http://localhost:8088" | ID Generator 服务地址 |
| `MinMessagesPerUser` | int | 3 | 每个用户最少消息数 |
| `MaxMessagesPerUser` | int | 10 | 每个用户最多消息数 |
| `ReadRatio` | double | 0.6 | 已读消息比例（0.0-1.0） |
| `DryRun` | switch | false | 仅预览，不创建 SQL 文件 |

### 配置文件格式

在 `test-data-config.json` 中配置：

```json
{
  "idGeneratorUrl": "http://localhost:8088"
}
```

注意：配置文件中暂未包含小助手消息的专门配置，使用命令行参数或默认值。

## 生成数据说明

### 消息类型

脚本支持 7 种消息类型：

1. **welcome**: 欢迎消息
   - 欢迎加入平台
   - 开始写作之旅
   - 始终是用户的第一条消息
   - 默认已读

2. **tip**: 使用技巧
   - Markdown 语法提示
   - 发布文章建议
   - 标签使用技巧
   - 代码块功能介绍

3. **achievement**: 成就通知
   - 第一个点赞
   - 发布文章里程碑
   - 收藏数达成
   - 关注者增长

4. **reminder**: 提醒消息
   - 发布文章提醒
   - 未读评论提醒
   - 回复粉丝提醒
   - 草稿箱提醒

5. **update**: 系统更新
   - 新功能上线
   - 性能优化
   - 功能改进

6. **activity**: 活动通知
   - 写作挑战
   - 征文活动
   - 线上分享会
   - 邀请奖励

7. **security**: 安全提醒
   - 异地登录提醒
   - 两步验证建议
   - 密码修改提醒

### 数据特征

- **数量**: 每个用户 3-10 条消息（默认）
- **已读状态**: 60% 的消息标记为已读（默认）
- **欢迎消息**: 每个用户的第一条消息始终是欢迎消息，且默认已读
- **消息链接**: 部分消息类型包含相关链接（如 tip、update、activity）
- **内容多样性**: 每种类型都有多个内容模板，随机选择

### 消息内容模板

每种消息类型都有多个内容模板：

- **welcome**: 3 个模板
- **tip**: 7 个模板
- **achievement**: 6 个模板
- **reminder**: 5 个模板
- **update**: 5 个模板
- **activity**: 4 个模板
- **security**: 3 个模板

总计 33 个不同的消息内容模板，确保消息内容的多样性。

## 输出文件

### SQL 文件

生成的 SQL 文件位于：`blog-microservice/database/test-data/sql/generated-assistant-messages.sql`

文件内容包括：
- 生成时间和统计信息
- 数据库连接命令
- 事务控制（BEGIN/COMMIT）
- INSERT 语句

### 执行 SQL 文件

```powershell
# 执行生成的 SQL 文件
psql -h localhost -p 5432 -U postgres -f "blog-microservice/database/test-data/sql/generated-assistant-messages.sql"
```

## 执行流程

1. **验证服务可用性**
   - 检查 ID Generator 服务是否运行
   - 生成测试 ID 验证服务正常

2. **获取测试用户**
   - 从数据库查询所有测试用户
   - 用于生成每个用户的消息

3. **生成小助手消息数据**
   - 为每个用户生成指定数量的消息
   - 第一条消息始终是欢迎消息
   - 随机选择其他消息类型和内容
   - 设置已读状态

4. **生成 SQL 文件**
   - 将消息数据转换为 SQL INSERT 语句
   - 保存到文件

5. **显示结果**
   - 统计总消息数和平均每用户消息数
   - 统计消息类型分布
   - 统计已读/未读状态

## 示例输出

```
╔════════════════════════════════════════════════════╗
║          小助手消息数据生成脚本                    ║
╚════════════════════════════════════════════════════╝

=== 步骤 1: 验证服务可用性 ===
  检查 ID Generator 服务...
✓ ID Generator 服务正常运行
  测试 ID: 1234567890123456789

=== 步骤 2: 获取测试用户 ===
  查询测试用户...
✓ 获取到 58 个测试用户

=== 步骤 3: 生成小助手消息数据 ===
  [==================================================] 100.0% - 生成消息: test_user_058 (7 条) (58/58)

=== 步骤 4: 生成 SQL 文件 ===
✓ SQL 文件生成成功
  文件路径: blog-microservice/database/test-data/sql/generated-assistant-messages.sql

=== 步骤 5: 生成结果 ===
✓ 小助手消息数据生成完成
  用户数: 58
  总消息数: 378
  平均每用户消息数: 6.5

=== 消息类型统计 ===
  achievement : 54 (14.3%)
  activity : 48 (12.7%)
  reminder : 52 (13.8%)
  security : 46 (12.2%)
  tip : 56 (14.8%)
  update : 64 (16.9%)
  welcome : 58 (15.3%)

=== 已读状态统计 ===
  已读: 227 (60.1%)
  未读: 151 (39.9%)

╔════════════════════════════════════════════════════╗
║          小助手消息数据生成成功！                  ║
╚════════════════════════════════════════════════════╝

下一步：执行 SQL 文件插入数据
  psql -h localhost -p 5432 -U postgres -f "blog-microservice/database/test-data/sql/generated-assistant-messages.sql"
```

## 数据验证

### 验证消息数量

```sql
-- 连接到数据库
\c blog_notification;

-- 查询消息总数
SELECT COUNT(*) FROM assistant_messages;

-- 查询每个用户的消息数
SELECT user_id, COUNT(*) as message_count
FROM assistant_messages
GROUP BY user_id
ORDER BY message_count DESC;

-- 查询已读消息数
SELECT COUNT(*) FROM assistant_messages WHERE is_read = TRUE;
```

### 验证消息类型分布

```sql
-- 按类型统计消息数量
SELECT type, COUNT(*) as count
FROM assistant_messages
GROUP BY type
ORDER BY count DESC;
```

### 验证欢迎消息

```sql
-- 验证每个用户都有欢迎消息
SELECT u.id, u.username, 
       EXISTS(
           SELECT 1 FROM assistant_messages 
           WHERE user_id = u.id AND type = 'welcome'
       ) as has_welcome
FROM users u
WHERE u.username LIKE 'test_%'
ORDER BY u.id;
```

### 验证用户关联

```sql
-- 验证所有消息都有有效的用户
SELECT COUNT(*) 
FROM assistant_messages am
LEFT JOIN users u ON am.user_id = u.id
WHERE u.id IS NULL;
-- 应该返回 0
```

## 故障排查

### 问题 1: ID Generator 服务不可用

**错误信息**：
```
✗ ID Generator 服务不可用
  错误: 无法连接到服务
```

**解决方案**：
1. 检查服务是否启动：`http://localhost:8088/api/v1/id/snowflake`
2. 检查端口是否被占用
3. 查看服务日志

### 问题 2: 未找到测试用户

**错误信息**：
```
✗ 获取测试用户失败: 未找到测试用户
```

**解决方案**：
1. 确认已执行用户数据生成脚本
2. 检查数据库中是否有测试用户：
   ```sql
   SELECT COUNT(*) FROM users WHERE username LIKE 'test_%';
   ```

### 问题 3: SQL 文件执行失败

**错误信息**：
```
ERROR: duplicate key value violates unique constraint
```

**解决方案**：
1. 清理旧数据：
   ```sql
   \c blog_notification;
   DELETE FROM assistant_messages;
   ```
2. 重新执行 SQL 文件

### 问题 4: 单引号转义问题

**错误信息**：
```
ERROR: syntax error at or near "t"
```

**解决方案**：
脚本已自动处理单引号转义，如果仍有问题，检查消息内容中的特殊字符。

## 需求验证

该脚本验证以下需求：

- ✅ **Requirements 9.4**: 为每个用户生成 3-10 条小助手消息
- ✅ **Requirements 9.5**: 生成不同类型的消息（7 种类型）
- ✅ **Requirements 9.6**: 为至少 60% 的小助手消息设置 is_read 为 true

## 相关文档

- [测试数据生成总览](../README.md)
- [全局公告生成](./README-ANNOUNCEMENTS.md)
- [通知数据生成](./README-NOTIFICATIONS.md)
- [数据库表结构](../../docker/postgres-init/02-init-tables.sql)

## 维护说明

### 添加新的消息类型

在脚本中的 `$MessageTemplates` 哈希表中添加新类型：

```powershell
$MessageTemplates = @{
    # ... 现有类型 ...
    NEW_TYPE = @{
        type = "new_type"
        messages = @(
            "新类型消息内容 1",
            "新类型消息内容 2"
        )
        link = "/new-link"  # 可选
    }
}
```

### 添加新的消息内容

在现有类型中添加新的消息内容：

```powershell
TIP = @{
    type = "tip"
    messages = @(
        # ... 现有消息 ...
        "新的提示消息内容"
    )
    link = "/help/markdown"
}
```

### 修改已读比例

通过命令行参数或修改默认值：

```powershell
# 命令行
.\Generate-AssistantMessages.ps1 -ReadRatio 0.8

# 或修改脚本中的默认值
param(
    [double]$ReadRatio = 0.8  # 修改为 80%
)
```

## 最后更新

- **日期**: 2026-02-13
- **版本**: 1.0.0
- **维护者**: 开发团队
