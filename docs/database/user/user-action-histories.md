# user_action_histories - 用户操作历史表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | user_action_histories |
| 描述 | 用户操作历史记录表，用于防刷统计 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| UserId | string(255) | 否 | - | 用户ID |
| ActionType | AntiSpamActionType | 否 | - | 操作类型枚举 |
| Action | UserAction | 否 | Create | 操作动作（Create/Delete等） |
| TargetId | string(255) | 是 | - | 目标ID（如文章ID、评论ID） |
| ActionTime | DateTimeOffset | 否 | - | 操作时间 |
| IpAddress | string(255) | 是 | - | IP地址 |

## 操作类型枚举 (AntiSpamActionType)

| 值 | 说明 |
|----|------|
| Comment | 评论 |
| Like | 点赞 |
| Favorite | 收藏 |
| Feedback | 反馈 |
| Report | 举报 |
| Message | 私信 |
| Follow | 关注 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_user_action_histories | Id | 主键 | 主键索引 |
| IX_UserActionHistory_UserId_ActionType | (UserId, ActionType) | 普通 | 查询用户某类操作 |
| IX_UserActionHistory_ActionTime | ActionTime | 普通 | 按时间查询 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | UserId | 多对一 | 操作用户 |

## 业务用途

1. **防刷限制**: 统计用户在时间窗口内的操作次数
2. **行为分析**: 分析用户行为模式
3. **安全审计**: 记录用户操作轨迹
