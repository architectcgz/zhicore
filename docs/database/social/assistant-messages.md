# assistant_messages - 小助手消息表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | assistant_messages |
| 描述 | 系统小助手发送给用户的消息表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 消息ID（雪花算法） |
| UserId | string | 否 | - | 接收消息的用户ID |
| Content | string(1000) | 否 | - | 消息内容 |
| Type | string(50) | 否 | - | 消息类型 |
| IsRead | bool | 否 | false | 是否已读 |
| Link | string(200) | 是 | - | 相关链接 |
| Metadata | string(2000) | 是 | - | 相关元数据（JSON格式） |
| CreateTime | DateTimeOffset | 否 | - | 创建时间 |
| ReadAt | DateTimeOffset | 是 | - | 阅读时间 |

## 消息类型 (Type)

| 值 | 说明 |
|----|------|
| post_approved | 文章通过审核 |
| post_rejected | 文章被拒绝 |
| post_taken_down | 文章被下架 |
| account_warning | 账号警告 |
| account_locked | 账号被锁定 |
| welcome | 欢迎消息 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_assistant_messages | Id | 主键 | 主键索引 |
| IX_AssistantMessage_UserId | UserId | 普通 | 查询用户消息 |
| IX_AssistantMessage_UserId_IsRead | (UserId, IsRead) | 普通 | 查询未读消息 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | UserId | 多对一 | 接收消息的用户 |

## Metadata 示例

```json
{
  "postId": 123456789,
  "postTitle": "我的第一篇文章",
  "reason": "内容违规"
}
```
