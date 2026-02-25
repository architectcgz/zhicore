# user_follows - 用户关注表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | user_follows |
| 描述 | 用户关注关系表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| FollowerId | string | 否 | - | 关注者用户ID |
| FollowingId | string | 否 | - | 被关注者用户ID |
| FollowTime | DateTimeOffset | 否 | - | 关注时间 |
| NotificationEnabled | bool | 否 | true | 是否启用通知 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_user_follows | Id | 主键 | 主键索引 |
| IX_UserFollow_FollowerId_FollowingId | (FollowerId, FollowingId) | 唯一 | 防止重复关注 |
| IX_UserFollow_FollowerId | FollowerId | 普通 | 查询关注列表 |
| IX_UserFollow_FollowingId | FollowingId | 普通 | 查询粉丝列表 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | FollowerId | 多对一 | 关注者 |
| AspNetUsers | FollowingId | 多对一 | 被关注者 |

## 业务规则

1. 用户不能关注自己
2. 同一用户对同一目标只能关注一次
3. 关注后可以选择是否接收对方发文通知
