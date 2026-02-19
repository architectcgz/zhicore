# messages - 私信消息表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | messages |
| 描述 | 用户私信消息表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 消息ID（雪花算法） |
| SenderId | string | 否 | - | 发送者用户ID |
| ReceiverId | string | 否 | - | 接收者用户ID |
| Content | string(2000) | 否 | - | 消息内容 |
| MessageType | short | 否 | 0 | 消息类型 |
| CreatedAt | DateTimeOffset | 否 | UtcNow | 创建时间 |
| UpdatedAt | DateTimeOffset | 否 | UtcNow | 更新时间 |
| Deleted | bool | 否 | false | 是否删除 |

## 消息类型 (MessageType)

| 值 | 说明 |
|----|------|
| 0 | 文本消息 |
| 1 | 图片消息 |
| 2 | 文件消息 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_messages | Id | 主键 | 主键索引 |
| IX_Message_SenderId_ReceiverId | (SenderId, ReceiverId) | 普通 | 查询两人之间的消息 |
| IX_Message_ReceiverId | ReceiverId | 普通 | 查询收到的消息 |
| IX_Message_CreatedAt | CreatedAt | 普通 | 按时间排序 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | SenderId | 多对一 | 发送者 |
| AspNetUsers | ReceiverId | 多对一 | 接收者 |

## 业务规则

1. 被拉黑的用户无法发送私信
2. 消息支持实时推送（通过 SignalR）
3. 消息删除使用软删除
