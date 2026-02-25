# post_likes - 文章点赞表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | post_likes |
| 描述 | 文章点赞记录表 |
| 主键 | (PostId, UserId) 复合主键 |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| PostId | long | 否 | - | 文章ID |
| UserId | string | 否 | - | 点赞用户ID |
| CreateTime | DateTimeOffset | 否 | - | 点赞时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_post_likes | (PostId, UserId) | 主键 | 复合主键索引 |
| IX_PostLike_UserId | UserId | 普通 | 查询用户点赞的文章 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| posts | PostId | 多对一 | 被点赞的文章 |
| AspNetUsers | UserId | 多对一 | 点赞用户 |

## 业务规则

1. 每个用户对同一篇文章只能点赞一次
2. 点赞后会更新 post_stats 表的 LikeCount
3. 点赞会触发通知给文章作者
4. 被拉黑的用户无法点赞
