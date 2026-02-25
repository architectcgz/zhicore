# conversations - 会话表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | conversations |
| 描述 | 用户会话状态表，跟踪已读位置 |
| 主键 | (From, To) 复合主键 |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| From | string(450) | 否 | - | 发送方用户ID |
| To | string(450) | 否 | - | 接收方用户ID |
| CheckpointMessageId | long | 否 | 0 | 检查点消息ID（最后已读消息ID） |
| LastMessageId | long | 是 | - | 最后消息ID |
| LastReadTime | DateTimeOffset | 否 | - | 最后已读时间 |
| CreateTime | DateTimeOffset | 否 | - | 创建时间 |
| UpdateTime | DateTimeOffset | 否 | - | 更新时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_conversations | (From, To) | 主键 | 复合主键索引 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | From | 多对一 | 发送方用户 |
| AspNetUsers | To | 多对一 | 接收方用户 |
| messages | LastMessageId | 多对一 | 最后一条消息 |

## 设计说明

每两个用户之间的会话，每个用户都有自己的记录：
- 用户A与用户B的会话：存在两条记录
  - (From=A, To=B): A视角的会话状态
  - (From=B, To=A): B视角的会话状态

### 未读消息计算

所有 `Id > CheckpointMessageId` 且 `SenderId = To` 的消息被视为未读。

```sql
SELECT COUNT(*) 
FROM messages 
WHERE sender_id = @To 
  AND receiver_id = @From 
  AND id > @CheckpointMessageId
  AND deleted = false;
```
