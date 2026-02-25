# notifications - 通知表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | notifications |
| 描述 | 用户通知表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 通知ID（雪花算法） |
| UserId | string | 否 | - | 接收通知的用户ID |
| Title | string(100) | 否 | - | 通知标题 |
| Content | string(2000) | 否 | - | 通知内容 |
| IsMarkdown | bool | 否 | false | 是否为Markdown格式 |
| Type | NotificationType | 否 | - | 通知类型 |
| Link | string(200) | 是 | - | 相关链接 |
| CreateTime | DateTimeOffset | 否 | - | 创建时间 |
| Deleted | bool | 否 | false | 逻辑删除标志 |
| IsRead | bool | 否 | false | 是否已读 |
| ReadAt | DateTimeOffset | 是 | - | 已读时间 |
| TriggerUserId | string(450) | 是 | - | 触发此通知的用户ID |
| TargetId | long | 是 | - | 相关目标ID |
| TargetType | NotificationTargetType | 是 | - | 目标类型 |

## 枚举定义

### NotificationType - 通知类型
| 值 | 说明 |
|----|------|
| System | 系统通知 |
| Like | 点赞通知 |
| Comment | 评论通知 |
| Follow | 关注通知 |
| Mention | @提及通知 |
| Reply | 回复通知 |

### NotificationTargetType - 目标类型
| 值 | 说明 |
|----|------|
| Post | 文章 |
| Comment | 评论 |
| Topic | 话题 |
| User | 用户 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_notifications | Id | 主键 | 主键索引 |
| IX_Notification_UserId | UserId | 普通 | 查询用户通知 |
| IX_Notification_UserId_IsRead | (UserId, IsRead) | 普通 | 查询未读通知 |
| IX_Notification_CreateTime | CreateTime | 普通 | 按时间排序 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | UserId | 多对一 | 接收通知的用户 |
| AspNetUsers | TriggerUserId | 多对一 | 触发通知的用户 |

## 业务规则

1. 通知支持实时推送（通过 SignalR）
2. 用户可以批量标记已读
3. 相同目标的通知可以聚合显示
