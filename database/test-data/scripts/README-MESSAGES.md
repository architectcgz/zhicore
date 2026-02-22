# 私信数据生成脚本说明

## 概述

`Generate-Messages.ps1` 脚本用于生成测试私信数据，包括会话和消息。

## 功能特性

### 会话生成（Requirements 7.1, 7.2, 7.3）

- 生成指定数量的私信会话
- 确保参与者 ID 顺序正确（participant1_id < participant2_id）
- 避免重复的会话对
- 会话在发送第一条消息时自动创建

### 消息生成（Requirements 7.4, 7.5, 7.6）

- 为每个会话生成 5-20 条消息
- 确保发送者和接收者是会话的参与者
- 使用预定义的消息内容模板
- 消息时间戳自动递增

### 已读状态（Requirements 7.7）

- 随机设置部分消息为已读
- 自动设置 read_at 时间戳

### 会话更新（Requirements 7.8, 7.9）

- 自动更新会话的最后消息信息
- 自动更新未读计数

## 使用方法

### 基本用法

```powershell
# 使用默认配置生成 50 个会话
.\Generate-Messages.ps1

# 使用自定义配置文件
.\Generate-Messages.ps1 -ConfigPath "..\test-data-config.json"

# 生成指定数量的会话
.\Generate-Messages.ps1 -ConversationCount 100

# 预览模式（不实际创建）
.\Generate-Messages.ps1 -DryRun
```

### 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| ConfigPath | string | "" | 配置文件路径 |
| ApiBaseUrl | string | http://localhost:8000 | API 基础地址 |
| IdGeneratorUrl | string | http://localhost:8088 | ID Generator 服务地址 |
| AppId | string | test-app | 应用 ID |
| ConversationCount | int | 50 | 要生成的会话数量 |
| MinMessagesPerConversation | int | 5 | 每个会话最少消息数 |
| MaxMessagesPerConversation | int | 20 | 每个会话最多消息数 |
| DryRun | switch | false | 仅预览，不实际创建 |

## 配置文件

配置文件使用 JSON 格式，示例：

```json
{
  "apiBaseUrl": "http://localhost:8000",
  "idGeneratorUrl": "http://localhost:8088",
  "appId": "test-app",
  "messages": {
    "conversationCount": 50,
    "messagesPerConversation": {
      "min": 5,
      "max": 20
    }
  }
}
```

## 前置条件

1. **服务运行**
   - ZhiCore-id-generator 服务已启动（端口 8088）
   - ZhiCore-gateway 服务已启动（端口 8000）
   - ZhiCore-message 服务已启动

2. **数据准备**
   - 已执行用户数据生成脚本（`Execute-UserGeneration.ps1`）
   - 数据库中存在测试用户

3. **网络连接**
   - 能够访问 API 服务
   - 能够访问 ID Generator 服务

## 执行流程

1. **验证服务可用性**
   - 检查 ID Generator 服务
   - 检查 API 服务

2. **获取测试用户**
   - 从数据库获取所有测试用户

3. **生成会话对**
   - 随机选择用户对
   - 确保 participant1_id < participant2_id
   - 避免重复的会话对

4. **生成消息**
   - 为每个会话生成 5-20 条消息
   - 随机选择发送者和接收者
   - 使用预定义的消息内容模板

5. **设置已读状态**
   - 随机标记部分消息为已读
   - 自动更新会话信息

## 生成的数据

### 会话数据

- **数量**: 50 个（默认）
- **参与者**: 两个不同的测试用户
- **顺序**: participant1_id < participant2_id
- **唯一性**: 每对用户只有一个会话

### 消息数据

- **数量**: 每个会话 5-20 条
- **类型**: 文本消息（TEXT）
- **内容**: 从预定义模板中随机选择
- **发送者**: 会话的两个参与者之一
- **接收者**: 会话的另一个参与者

### 已读状态

- **概率**: 50% 的会话标记为已读
- **范围**: 标记会话中的所有消息
- **时间戳**: 自动设置 read_at

## 输出示例

```
╔════════════════════════════════════════════════════╗
║          私信数据生成脚本                          ║
╚════════════════════════════════════════════════════╝

=== 步骤 1: 验证服务可用性 ===
  检查 ID Generator 服务...
✓ ID Generator 服务正常运行
  测试 ID: 1234567890123456789
  检查 API 服务...
✓ API 服务正常运行
  状态: UP

=== 步骤 2: 获取测试用户 ===
  获取测试用户列表...
✓ 获取到 58 个测试用户

=== 步骤 3: 生成会话对 ===
✓ 会话对生成完成
  生成数量: 50

=== 步骤 4: 生成消息 ===
  [==================================================] 100.0% - 生成会话消息: user001 <-> user002 (12 条) (50/50)

=== 步骤 5: 生成结果 ===
✓ 私信数据生成完成
  成功会话: 50
  总消息数: 625
  失败会话: 0

=== 统计信息 ===
  目标会话数: 50
  成功会话数: 50
  失败会话数: 0
  总消息数: 625
  平均每会话消息数: 12.5
  成功率: 100.00%

╔════════════════════════════════════════════════════╗
║          私信数据生成成功！                        ║
╚════════════════════════════════════════════════════╝
```

## 错误处理

### 常见错误

1. **服务不可用**
   ```
   ✗ API 服务不可用
   错误: 无法连接到服务
   请确保服务已启动: http://localhost:8000
   ```
   **解决方案**: 启动相关服务

2. **未找到测试用户**
   ```
   ✗ 获取测试用户失败: 未找到测试用户
   请确保已执行用户数据生成脚本
   ```
   **解决方案**: 先执行 `Execute-UserGeneration.ps1`

3. **发送消息失败**
   ```
   ⚠ 发送消息失败: API 返回错误
   ```
   **解决方案**: 检查 API 日志，确认服务状态

### 重试机制

- API 调用失败时自动重试 3 次
- 每次重试间隔 2 秒
- 超过最大重试次数后报告错误

## 数据验证

### 验证项

1. **会话参与者顺序**
   - participant1_id < participant2_id

2. **消息发送者接收者**
   - sender_id 和 receiver_id 必须是会话的参与者

3. **会话唯一性**
   - 每对用户只有一个会话

4. **消息数量**
   - 每个会话至少 5 条消息
   - 每个会话最多 20 条消息

### 验证方法

可以使用以下 SQL 查询验证数据：

```sql
-- 验证会话参与者顺序
SELECT COUNT(*) FROM conversations 
WHERE participant1_id >= participant2_id;
-- 应该返回 0

-- 验证消息发送者接收者
SELECT COUNT(*) FROM messages m
JOIN conversations c ON m.conversation_id = c.id
WHERE (m.sender_id != c.participant1_id AND m.sender_id != c.participant2_id)
   OR (m.receiver_id != c.participant1_id AND m.receiver_id != c.participant2_id);
-- 应该返回 0

-- 验证会话唯一性
SELECT participant1_id, participant2_id, COUNT(*) 
FROM conversations 
GROUP BY participant1_id, participant2_id 
HAVING COUNT(*) > 1;
-- 应该返回空结果

-- 验证消息数量
SELECT conversation_id, COUNT(*) as message_count
FROM messages
GROUP BY conversation_id
HAVING COUNT(*) < 5 OR COUNT(*) > 20;
-- 应该返回空结果或接近空结果
```

## 性能考虑

### 执行时间

- 50 个会话，约 625 条消息：约 2-3 分钟
- 100 个会话，约 1250 条消息：约 5-6 分钟

### 优化建议

1. **批量操作**: 当前实现为单条消息发送，可以考虑批量 API
2. **并发处理**: 可以使用 PowerShell 的并行功能加速
3. **缓存用户**: 减少重复的用户查询

## 故障排查

### 检查服务状态

```powershell
# 检查 ID Generator 服务
Invoke-RestMethod -Uri "http://localhost:8088/api/v1/id/snowflake"

# 检查 API 服务
Invoke-RestMethod -Uri "http://localhost:8000/actuator/health"

# 检查测试用户
Invoke-RestMethod -Uri "http://localhost:8000/api/v1/users/test-users" `
    -Headers @{"X-App-Id" = "test-app"}
```

### 查看日志

- API 服务日志: `ZhiCore-microservice/ZhiCore-message/logs/`
- 脚本执行日志: 控制台输出

### 清理测试数据

如果需要重新生成数据，可以先清理：

```sql
-- 清理私信数据
DELETE FROM messages WHERE conversation_id IN (
    SELECT id FROM conversations 
    WHERE participant1_id IN (SELECT id FROM users WHERE username LIKE 'test_%')
);

DELETE FROM conversations 
WHERE participant1_id IN (SELECT id FROM users WHERE username LIKE 'test_%');
```

## 相关文档

- [测试数据生成总览](../README.md)
- [用户数据生成](./README-USERS.md)
- [API 测试规范](../../../../.kiro/steering/10-api-testing.md)
- [PowerShell 脚本规范](../../../../.kiro/steering/09-powershell.md)

## 维护

- **维护者**: 开发团队
- **最后更新**: 2026-02-13
- **版本**: 1.0.0
