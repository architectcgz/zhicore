# comments - 评论表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | comments |
| 描述 | 文章评论表，支持多级回复 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| RootId | long | 否 | - | 根评论ID（顶级评论的RootId与Id相同） |
| ParentId | long | 是 | - | 父评论ID（顶级评论则为NULL） |
| AuthorId | string | 否 | - | 评论作者ID |
| Content | string(500) | 否 | "" | 评论内容 |
| PostId | long | 否 | - | 评论所属文章ID |
| IpAddress | string(45) | 是 | - | 评论者IP地址（支持IPv6） |
| UserAgent | string(500) | 是 | - | 评论者用户代理 |
| CreateTime | DateTimeOffset | 否 | - | 评论创建时间 |
| UpdateTime | DateTimeOffset | 否 | - | 评论最后更新时间 |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_comments | Id | 主键 | 主键索引 |
| IX_Comment_PostId | PostId | 普通 | 查询文章评论 |
| IX_Comment_AuthorId | AuthorId | 普通 | 查询用户评论 |
| IX_Comment_RootId | RootId | 普通 | 查询评论回复 |
| IX_Comment_ParentId | ParentId | 普通 | 查询直接回复 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| posts | PostId | 多对一 | 所属文章 |
| AspNetUsers | AuthorId | 多对一 | 评论作者 |
| comments | RootId | 自关联 | 根评论 |
| comments | ParentId | 自关联 | 父评论 |
| comment_stats | CommentId | 一对一 | 评论统计 |
| post_comment_likes | CommentId | 一对多 | 评论点赞 |

## 评论层级结构

```
评论A (Id=1, RootId=1, ParentId=null)  -- 顶级评论
├── 回复B (Id=2, RootId=1, ParentId=1)  -- 对A的回复
│   └── 回复D (Id=4, RootId=1, ParentId=2)  -- 对B的回复
└── 回复C (Id=3, RootId=1, ParentId=1)  -- 对A的回复
```

## 业务规则

1. 顶级评论的 RootId 等于自身 Id，ParentId 为 null
2. 回复评论的 RootId 指向顶级评论，ParentId 指向被回复的评论
3. 评论创建后会更新 post_stats 表的 CommentCount
4. 评论会触发通知给文章作者和被回复者
5. 被拉黑的用户无法评论
