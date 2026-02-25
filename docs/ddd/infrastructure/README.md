# Infrastructure（基础设施）

## 概述

基础设施层提供技术实现，包括数据持久化、缓存、消息队列、服务降级等。在 DDD 架构中，基础设施层实现领域层定义的接口。

## 组件清单

| 组件 | 职责 | 技术 |
|------|------|------|
| Repository | 数据持久化 | EF Core + PostgreSQL |
| Caching | 缓存管理 | Redis + Memory Cache |
| Events | 领域事件分发 | InProcess / RabbitMQ |
| Resilience | 服务降级 | Polly |
| Search | 全文搜索 | Elasticsearch |

## 目录结构

```
ZhiCoreCore/Infrastructure/
├── Repositories/               # Repository 实现
│   ├── PostRepository.cs
│   ├── CachedPostRepository.cs
│   ├── UserRepository.cs
│   ├── CommentRepository.cs
│   └── ...
├── Caching/                    # 缓存服务
│   ├── CachedPostService.cs
│   ├── CachedUserService.cs
│   ├── CachedCommentService.cs
│   └── ...
├── Events/                     # 事件基础设施
│   ├── InProcessDomainEventDispatcher.cs
│   ├── RabbitMqDomainEventDispatcher.cs
│   └── RedisProcessedEventStore.cs
├── Resilience/                 # 弹性策略
│   ├── ResiliencePolicies.cs
│   ├── ResilientSearchService.cs
│   └── ResilientEventPublisher.cs
└── UnitOfWork.cs               # 工作单元
```

## 详细文档

- [缓存策略](./caching.md)
- [服务降级](./resilience.md)
- [领域事件](./events.md)
- [防刷机制解耦](./anti-spam-decoupling.md)
- [防刷机制最佳实践](./anti-spam-best-practices.md)
