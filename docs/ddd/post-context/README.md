# Post Context（文章上下文）

## 概述

文章上下文是博客系统的核心限界上下文，负责文章的完整生命周期管理，包括创建、发布、编辑、删除，以及相关的点赞、收藏、阅读量统计等功能。

## 聚合根

### PostAggregate

```
PostAggregate
├── Post（聚合根）
│   ├── Id: long (雪花ID)
│   ├── Title: string
│   ├── Raw: string (原始内容)
│   ├── Html: string (渲染后内容)
│   ├── Excerpt: string (摘要)
│   ├── OwnerId: string
│   ├── Status: PostStatus
│   ├── TopicId: long?
│   └── PublishedAt: DateTimeOffset
├── PostStats（值对象）
│   ├── LikeCount: int
│   ├── CommentCount: int
│   ├── FavoriteCount: int
│   └── ViewCount: long
└── PostCategory（关联实体）
    ├── PostId: long
    └── CategoryId: int
```

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| PostService | IPostService | 12+ | 文章 CRUD、发布、草稿管理 |
| PostLikeService | IPostLikeService | 11 | 文章点赞/取消点赞 |
| PostFavoriteService | IPostFavoriteService | 8 | 文章收藏/取消收藏 |
| PostStatsService | IPostStatsService | 4 | 文章统计（点赞数、评论数等） |
| PostViewCountService | IViewCountService | 3 | 文章阅读量统计 |
| PostHotnessService | IPostHotnessService | 6 | 文章热度计算 |
| CachedPostService | IPostService | 6 | 文章缓存装饰器 |
| CachedViewCountService | IViewCountService | 4 | 阅读量缓存装饰器 |

## 领域事件

### 发布的事件

| 事件 | 触发场景 | 处理器 |
|------|---------|--------|
| PostPublishedEvent | 文章发布 | RankingHandler, SearchHandler |
| PostUpdatedEvent | 文章更新 | SearchHandler, CacheHandler |
| PostDeletedEvent | 文章删除 | SearchHandler, RankingHandler, CacheHandler |
| PostLikedEvent | 文章点赞 | NotificationHandler, RankingHandler |
| PostFavoritedEvent | 文章收藏 | NotificationHandler |
| PostViewedEvent | 文章浏览 | RankingHandler |

### 订阅的事件

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| CommentCreatedEvent | Comment Context | 更新 PostStats.CommentCount |
| CommentDeletedEvent | Comment Context | 更新 PostStats.CommentCount |
| UserProfileUpdatedEvent | User Context | 更新缓存的作者信息 |

## 目录结构

```
BlogCore/
├── Application/Post/
│   ├── IPostApplicationService.cs
│   ├── PostApplicationService.cs
│   ├── IPostLikeApplicationService.cs
│   ├── PostLikeApplicationService.cs
│   ├── IPostFavoriteApplicationService.cs
│   ├── PostFavoriteApplicationService.cs
│   ├── IPostViewApplicationService.cs
│   └── PostViewApplicationService.cs
├── Domain/
│   ├── Repositories/
│   │   ├── IPostRepository.cs
│   │   ├── IPostLikeRepository.cs
│   │   ├── IPostFavoriteRepository.cs
│   │   └── IPostStatsRepository.cs
│   ├── Services/
│   │   ├── IPostDomainService.cs
│   │   └── PostDomainService.cs
│   └── EventHandlers/Post/
│       ├── PostPublishedEventHandler.cs
│       ├── PostLikedEventHandler.cs
│       └── PostViewedEventHandler.cs
└── Infrastructure/Repositories/
    ├── PostRepository.cs
    ├── CachedPostRepository.cs
    ├── PostLikeRepository.cs
    └── PostStatsRepository.cs
```

## 详细文档

- [PostService 详细设计](./post-service.md)
- [PostLikeService 详细设计](./post-like-service.md)
- [PostFavoriteService 详细设计](./post-favorite-service.md)
- [PostStatsService 详细设计](./post-stats-service.md)
- [ViewCountService 详细设计](./view-count-service.md)
