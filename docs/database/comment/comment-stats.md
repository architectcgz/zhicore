# comment_stats - 评论统计表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | comment_stats |
| 描述 | 评论统计信息表 |
| 主键 | CommentId (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| CommentId | long | 否 | - | 评论ID（主键，关联comments表） |
| LikeCount | int | 否 | 0 | 点赞数 |
| ReplyCount | int | 否 | 0 | 回复数 |
| HotScore | int | 否 | 0 | 热度分数（应用层计算） |
| CreateTime | DateTimeOffset | 否 | UtcNow | 统计信息创建时间 |
| UpdateTime | DateTimeOffset | 否 | UtcNow | 统计信息更新时间 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_comment_stats | CommentId | 主键 | 主键索引 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| comments | CommentId | 一对一 | 关联评论 |

## 热度计算公式

```
HotScore = (LikeCount * like_weight + ReplyCount * reply_weight) 
           / (1 + time_decay_factor * age / decay_scaling_factor)
```

其中：
- `like_weight`: 点赞权重
- `reply_weight`: 回复权重
- `time_decay_factor`: 时间衰减因子
- `age`: 评论发布后的时间
- `decay_scaling_factor`: 衰减缩放因子
