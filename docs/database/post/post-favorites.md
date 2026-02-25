# post_favorites - 文章收藏表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | post_favorites |
| 描述 | 文章收藏记录表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| PostId | long | 否 | - | 文章ID |
| UserId | string | 否 | - | 用户ID |
| CreateTime | DateTimeOffset | 否 | - | 收藏时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_post_favorites | Id | 主键 | 主键索引 |
| IX_PostFavorite_PostId_UserId | (PostId, UserId) | 唯一 | 防止重复收藏 |
| IX_PostFavorite_UserId | UserId | 普通 | 查询用户收藏列表 |
| IX_PostFavorite_PostId | PostId | 普通 | 查询文章被收藏情况 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| posts | PostId | 多对一 | 被收藏的文章 |
| AspNetUsers | UserId | 多对一 | 收藏用户 |

## 业务规则

1. 每个用户对同一篇文章只能收藏一次
2. 收藏后会更新 post_stats 表的 FavoriteCount
3. 用户可以查看自己的收藏列表
