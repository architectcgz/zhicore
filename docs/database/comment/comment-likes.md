# post_comment_likes - 评论点赞表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | post_comment_likes |
| 描述 | 评论点赞记录表 |
| 主键 | (CommentId, UserId) 复合主键 |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| CommentId | long | 否 | - | 评论ID |
| UserId | string | 否 | - | 点赞用户ID |
| CreateTime | DateTimeOffset | 否 | - | 点赞时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_post_comment_likes | (CommentId, UserId) | 主键 | 复合主键索引 |
| IX_CommentLike_UserId | UserId | 普通 | 查询用户点赞的评论 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| comments | CommentId | 多对一 | 被点赞的评论 |
| AspNetUsers | UserId | 多对一 | 点赞用户 |

## 业务规则

1. 每个用户对同一条评论只能点赞一次
2. 点赞后会更新 comment_stats 表的 LikeCount
3. 点赞会触发通知给评论作者
