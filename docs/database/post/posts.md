# posts - 文章表

## 表信息

| 属性 | 值 |
|------|-----|
| 表名 | posts |
| 描述 | 文章主表 |
| 主键 | Id (long) |

## 字段定义

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|--------|------|------|--------|------|
| Id | long | 否 | - | 主键ID（雪花算法） |
| Title | string(50) | 否 | "" | 文章标题 |
| OwnerId | string | 否 | - | 文章所属用户ID |
| Raw | string | 否 | "" | 文章原始内容（Markdown/富文本源码） |
| Html | string | 否 | "" | 渲染为HTML后的文章 |
| Excerpt | string(300) | 是 | "" | 文章摘录 |
| CoverImage | string(300) | 是 | "" | 文章封面图片URL |
| CreateTime | DateTimeOffset | 否 | - | 文章创建时间 |
| UpdateTime | DateTimeOffset | 否 | - | 文章更新时间 |
| PublishedAt | DateTimeOffset | 否 | - | 文章发布时间 |
| Status | PostStatus | 否 | - | 文章状态 |
| Type | PostType | 否 | - | 文章类型 |
| Format | PostFormat | 否 | - | 文章格式 |
| Visibility | Visibility | 否 | - | 文章可见性 |
| Tags | List<string> | 是 | [] | 文章标签列表 |
| TopicId | long | 是 | - | 文章所属话题ID |
| Deleted | bool | 否 | false | 逻辑删除标志 |

## 枚举定义

### PostStatus - 文章状态
| 值 | 说明 |
|----|------|
| Draft | 草稿 |
| Published | 已发布 |
| Archived | 已归档 |
| PendingReview | 待审核 |
| Rejected | 已拒绝 |

### PostType - 文章类型
| 值 | 说明 |
|----|------|
| Original | 原创 |
| Repost | 转载 |
| Translation | 翻译 |

### PostFormat - 文章格式
| 值 | 说明 |
|----|------|
| Markdown | Markdown格式 |
| RichText | 富文本格式 |

### Visibility - 可见性
| 值 | 说明 |
|----|------|
| Public | 公开 |
| Private | 私密 |
| FollowersOnly | 仅关注者可见 |

## 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| PK_posts | Id | 主键 | 主键索引 |
| IX_Post_OwnerId | OwnerId | 普通 | 查询用户文章 |
| IX_Post_TopicId | TopicId | 普通 | 查询话题文章 |
| IX_Post_Status | Status | 普通 | 按状态筛选 |
| IX_Post_PublishedAt | PublishedAt | 普通 | 按发布时间排序 |

## 关联关系

| 关联表 | 关联字段 | 关系类型 | 说明 |
|--------|----------|----------|------|
| AspNetUsers | OwnerId | 多对一 | 文章作者 |
| topics | TopicId | 多对一 | 所属话题 |
| post_stats | PostId | 一对一 | 文章统计 |
| post_likes | PostId | 一对多 | 文章点赞 |
| post_favorites | PostId | 一对多 | 文章收藏 |
| comments | PostId | 一对多 | 文章评论 |
| post_category | PostId | 一对多 | 文章分类 |
