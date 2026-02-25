# topic_stats - 话题统计表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | topic_stats |
| 描述 | 话题统计信息表 |
| 主键 | TopicId (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| TopicId | long | 否 | - | 话题ID（主键，与topics表一对一关联） |
| PostCount | int | 否 | 0 | 关联话题的文章数量 |
| DiscussionCount | int | 否 | 0 | 话题下的总评论数（讨论数） |
| LikeCount | int | 否 | 0 | 话题下的总点赞数 |
| ViewCount | long | 否 | 0 | 话题下的总浏览数 |
| FollowerCount | int | 否 | 0 | 关注该话题的用户数量 |
| LastUpdateTime | DateTimeOffset | 否 | - | 统计信息最后更新时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_topic_stats | TopicId | 主键 | 主键索引 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| topics | TopicId | 一对一 | 关联话题 |

## 统计更新触发条件

| 字段 | 触发条件 |
|------|----------|
| PostCount | 文章发布/删除到该话题 |
| DiscussionCount | 话题下文章有新评论 |
| LikeCount | 话题下文章被点赞 |
| ViewCount | 话题下文章被浏览 |
| FollowerCount | 用户关注/取消关注话题 |
