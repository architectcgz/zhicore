# Comment Context（评论上下文）

## 概述

评论上下文负责评论相关的所有功能，包括评论的创建、删除、点赞，以及评论统计等。

## 聚合根

### CommentAggregate

```
CommentAggregate
├── Comment（聚合根）
│   ├── Id: long (雪花ID)
│   ├── Content: string
│   ├── PostId: long
│   ├── AuthorId: string
│   ├── ParentId: long? (父评论ID)
│   ├── RootId: long (根评论ID)
│   └── CreateTime: DateTimeOffset
└── CommentStats（值对象）
    ├── LikeCount: int
    └── ReplyCount: int
```

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| CommentService | ICommentService | 10 | 评论 CRUD |
| CommentLikeService | ICommentLikeService | 10 | 评论点赞/取消点赞 |
| CommentStatsService | ICommentStatsService | 5 | 评论统计 |
| CommentReplyStatsService | ICommentReplyStatsService | 4 | 评论回复统计 |
| CommentCacheService | ICommentCacheService | 5 | 评论缓存 |
| HotPostCommentCacheService | IHotPostCommentCacheService | 4 | 热门文章评论缓存 |
| CachedCommentService | ICommentService | 4 | 评论缓存装饰器 |

## 领域事件

### 发布的事件

| 事件 | 触发场景 | 处理器 |
|------|---------|--------|
| CommentCreatedEvent | 评论创建 | PostStatsHandler, NotificationHandler |
| CommentDeletedEvent | 评论删除 | PostStatsHandler |
| CommentLikedEvent | 评论点赞 | NotificationHandler |

### 订阅的事件

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| UserProfileUpdatedEvent | User Context | 更新缓存的用户信息 |
| PostDeletedEvent | Post Context | 清理相关评论缓存 |

## 目录结构

```
BlogCore/
├── Application/Comment/
│   ├── ICommentApplicationService.cs
│   ├── CommentApplicationService.cs
│   ├── ICommentLikeApplicationService.cs
│   └── CommentLikeApplicationService.cs
├── Domain/
│   ├── Repositories/
│   │   ├── ICommentRepository.cs
│   │   └── ICommentLikeRepository.cs
│   ├── Services/
│   │   ├── ICommentDomainService.cs
│   │   └── CommentDomainService.cs
│   └── EventHandlers/Comment/
│       ├── CommentCreatedEventHandler.cs
│       └── CommentLikedEventHandler.cs
└── Infrastructure/Repositories/
    ├── CommentRepository.cs
    └── CommentLikeRepository.cs
```

## 详细文档

- [CommentService 详细设计](./comment-service.md) ✅
- [CommentLikeService 详细设计](./comment-like-service.md) ✅
- [CommentStatsService 详细设计](./comment-stats-service.md) ✅
