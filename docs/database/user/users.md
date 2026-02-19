# AspNetUsers - 用户表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | AspNetUsers |
| 描述 | 用户信息表，继承自 ASP.NET Identity |
| 主键 | Id (string) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | string | 否 | - | 用户ID（GUID格式） |
| UserName | string | 否 | - | 用户名（唯一） |
| NormalizedUserName | string | 否 | - | 标准化用户名（大写） |
| Email | string | 是 | - | 邮箱地址 |
| NormalizedEmail | string | 是 | - | 标准化邮箱（大写） |
| EmailConfirmed | bool | 否 | false | 邮箱是否已验证 |
| PasswordHash | string | 是 | - | 密码哈希值 |
| SecurityStamp | string | 是 | - | 安全戳（用于令牌验证） |
| ConcurrencyStamp | string | 是 | - | 并发戳 |
| PhoneNumber | string | 是 | - | 手机号码 |
| PhoneNumberConfirmed | bool | 否 | false | 手机号是否已验证 |
| TwoFactorEnabled | bool | 否 | false | 是否启用双因素认证 |
| LockoutEnd | DateTimeOffset | 是 | - | 锁定结束时间 |
| LockoutEnabled | bool | 否 | true | 是否启用锁定功能 |
| AccessFailedCount | int | 否 | 0 | 登录失败次数 |
| NickName | string | 否 | "" | 用户昵称 |
| Bio | string | 是 | - | 个人简介 |
| AvatarUrl | string | 是 | - | 头像URL |
| CreateTime | DateTimeOffset | 否 | - | 创建时间 |
| UpdateTime | DateTimeOffset | 否 | - | 更新时间 |
| IsActive | bool | 否 | true | 是否激活 |
| RefreshToken | string | 是 | - | 刷新令牌 |
| RefreshTokenExpiryTime | DateTimeOffset | 是 | - | 刷新令牌过期时间 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_AspNetUsers | Id | 主键 | 主键索引 |
| IX_AspNetUsers_NormalizedUserName | NormalizedUserName | 唯一 | 用户名唯一索引 |
| IX_AspNetUsers_NormalizedEmail | NormalizedEmail | 普通 | 邮箱索引 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| posts | OwnerId | 一对多 | 用户发布的文章 |
| comments | AuthorId | 一对多 | 用户发表的评论 |
| user_follows | FollowerId | 一对多 | 用户关注的人 |
| user_follows | FollowingId | 一对多 | 关注用户的人 |
| user_follow_stats | UserId | 一对一 | 用户关注统计 |
| user_blocks | BlockerId | 一对多 | 用户拉黑的人 |
| user_blocks | BlockedUserId | 一对多 | 拉黑用户的人 |
| notifications | UserId | 一对多 | 用户收到的通知 |
| messages | SenderId | 一对多 | 用户发送的消息 |
| messages | ReceiverId | 一对多 | 用户收到的消息 |
| post_likes | UserId | 一对多 | 用户点赞的文章 |
| post_favorites | UserId | 一对多 | 用户收藏的文章 |
| post_comment_likes | UserId | 一对多 | 用户点赞的评论 |
| AspNetUserRoles | UserId | 一对多 | 用户的角色 |

## 示例数据

```json
{
  "Id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "UserName": "john_doe",
  "NormalizedUserName": "JOHN_DOE",
  "Email": "john@example.com",
  "NormalizedEmail": "JOHN@EXAMPLE.COM",
  "EmailConfirmed": true,
  "NickName": "John",
  "Bio": "A passionate developer",
  "AvatarUrl": "https://example.com/avatar.jpg",
  "CreateTime": "2024-01-01T00:00:00+00:00",
  "UpdateTime": "2024-01-15T10:30:00+00:00",
  "IsActive": true,
  "Deleted": false
}
```
