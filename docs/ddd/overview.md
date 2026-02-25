# DDD 架构总览

## 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                          │
│                    (Controllers, API)                            │
│         - 接收 HTTP 请求，返回响应                                │
│         - 参数验证、权限检查                                      │
│         - 调用 Application Service                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Application Layer                            │
│              (Application Services, DTOs)                        │
│         - 用例协调、事务管理、事件发布                            │
│         - 不包含业务逻辑，只做编排                                │
│         - 依赖：Repository, Domain Service, Event Dispatcher     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Domain Layer                               │
│     (Domain Services, Entities, Value Objects, Events)           │
│         - 核心业务逻辑                                           │
│         - 领域规则和不变量                                        │
│         - Repository 接口定义                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                           │
│        (Repositories, DbContext, External Services)              │
│         - 数据持久化（Repository 实现）                          │
│         - 缓存（Cached Repository / Cache Decorator）            │
│         - 外部服务集成（MQ、ES、Redis）                          │
│         - 服务降级（Polly 策略）                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 限界上下文

> **注意**：Social Context 中的 Message 模块已重新定位为"博客消息业务编排层"，私信功能委托给独立的 **im-system** 服务。详见 [ZhiCore-message 与 im-system 集成架构](../architecture/ZhiCore-message-im-integration.md)。

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           ZhiCore System                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ Post Context │  │ User Context │  │Comment Context│ │Social Context│ │
│  │              │  │              │  │              │  │              │ │
│  │ - Post       │  │ - User       │  │ - Comment    │  │ - Follow     │ │
│  │ - Draft      │  │ - Session    │  │ - CommentLike│  │ - Notification│ │
│  │ - Category   │  │ - Role       │  │ - CommentStats│ │ - Message*   │ │
│  │ - PostStats  │  │ - Activity   │  │              │  │   (适配层)   │ │
│  │ - PostLike   │  │ - CheckIn    │  │              │  │              │ │
│  │ - PostFavorite│ │ - Block      │  │              │  │              │ │
│  │ - ViewCount  │  │ - History    │  │              │  │              │ │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘ │
│         │                 │                 │                 │          │
│         └─────────────────┴─────────────────┴─────────────────┘          │
│                                    │                                     │
│                           ┌────────▼────────┐                           │
│                           │  Domain Events  │                           │
│                           │ (InProcess/MQ)  │                           │
│                           └────────┬────────┘                           │
│                                    │                                     │
│  ┌──────────────┐  ┌──────────────┐│ ┌──────────────┐  ┌──────────────┐ │
│  │Ranking Context│ │Content Context│ │ Auth Context │  │Search Context│ │
│  │              │  │              │  │              │  │              │ │
│  │ - PostHotness│  │ - Topic      │  │ - Auth       │  │ - PostSearch │ │
│  │ - CreatorHot │  │ - Report     │  │ - AntiSpam   │  │ - UserSearch │ │
│  │ - TopicHotness│ │ - Feedback   │  │ - Jwt        │  │              │ │
│  │ - Ranking    │  │ - Announce   │  │              │  │              │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘ │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐                                     │
│  │ Admin Context│  │ Data Context │                                     │
│  │              │  │              │                                     │
│  │ - AdminUser  │  │ - Reconcile  │                                     │
│  │ - AdminPost  │  │ - Summary    │                                     │
│  │ - AdminComment│ │ - Seeder     │                                     │
│  │ - AdminReport│  │              │                                     │
│  └──────────────┘  └──────────────┘                                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 依赖规则

### 严格分层原则

| 层级 | 可依赖 | 不可依赖 |
|------|--------|---------|
| Presentation | Application | Domain, Infrastructure |
| Application | Domain, Infrastructure (通过接口) | Presentation |
| Domain | 无外部依赖 | Application, Infrastructure, Presentation |
| Infrastructure | Domain (实现接口) | Application, Presentation |

### 数据访问规则

- **Application 层不直接使用 DbContext**
- 所有数据访问通过 Repository 接口
- Repository 接口定义在 Domain 层
- Repository 实现在 Infrastructure 层

## 目录结构

```
ZhiCoreCore/
├── Application/                    # 应用层
│   ├── Post/
│   │   ├── IPostApplicationService.cs
│   │   ├── PostApplicationService.cs
│   │   ├── IPostLikeApplicationService.cs
│   │   └── PostLikeApplicationService.cs
│   ├── Comment/
│   ├── User/
│   └── Social/
├── Domain/                         # 领域层
│   ├── Entities/                   # 领域实体（复用现有 PO）
│   ├── Services/                   # 领域服务
│   │   ├── IPostDomainService.cs
│   │   └── PostDomainService.cs
│   ├── Repositories/               # Repository 接口
│   │   ├── IPostRepository.cs
│   │   └── IUserRepository.cs
│   ├── Events/                     # 领域事件
│   │   ├── IDomainEvent.cs
│   │   ├── IDomainEventDispatcher.cs
│   │   └── IDomainEventHandler.cs
│   └── EventHandlers/              # 事件处理器
│       ├── Post/
│       ├── Comment/
│       └── User/
└── Infrastructure/                 # 基础设施层
    ├── Repositories/               # Repository 实现
    │   ├── PostRepository.cs
    │   └── CachedPostRepository.cs
    ├── Caching/                    # 缓存服务
    │   ├── CachedPostService.cs
    │   └── CachedUserService.cs
    ├── Resilience/                 # 弹性策略
    │   ├── ResiliencePolicies.cs
    │   └── ResilientSearchService.cs
    └── Events/                     # 事件基础设施
        ├── InProcessDomainEventDispatcher.cs
        └── RabbitMqDomainEventDispatcher.cs
```

## 领域事件流

### 事件分发模式

1. **InProcess（默认）** - 进程内同步分发，不依赖外部 MQ
2. **RabbitMQ（可选）** - 异步分发，用于跨进程通信

### 事件流示例

```
PostService.PublishPostAsync()
    │
    ├── 1. 创建 Post 实体
    ├── 2. 保存到数据库（通过 Repository）
    ├── 3. 发布 PostPublishedEvent
    │       │
    │       ├── PostPublishedEventHandler
    │       │   └── 初始化文章热度
    │       │
    │       └── SearchIndexEventHandler
    │           └── 索引到 Elasticsearch
    │
    └── 4. 返回 PostId
```

## 缓存策略

### 缓存层级

```
┌─────────────────────────────────────────────────────────────────┐
│                     Application Layer                            │
│         - 不直接操作缓存                                          │
│         - 通过 Repository 或 Cached Service 访问数据              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                           │
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐             │
│  │  Cached Repository  │    │   Cache Decorator   │             │
│  │  (方式一)           │    │   (方式二)          │             │
│  │                     │    │                     │             │
│  │  Repository 内部    │    │  装饰器模式包装     │             │
│  │  集成缓存逻辑       │    │  原有 Service       │             │
│  └─────────────────────┘    └─────────────────────┘             │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Redis / Memory Cache                      ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 缓存策略选择

| 场景 | 推荐方式 | 说明 |
|------|---------|------|
| 单实体查询 | Cached Repository | 缓存粒度细，易于失效 |
| 列表查询 | Cached Repository | 可以缓存查询结果 |
| 复杂业务逻辑 | Cache Decorator | 缓存整个服务方法的结果 |
| 统计数据 | Redis 原子操作 | 直接使用 Redis INCR/DECR |
| 热点数据 | 专用缓存服务 | 如 HotnessResultCache |

## 服务降级

### 降级策略

```
┌─────────────────────────────────────────────────────────────────┐
│              Resilience Decorator / Policy                       │
│                                                                  │
│  - Circuit Breaker（熔断器）                                     │
│  - Retry（重试）                                                 │
│  - Fallback（降级）                                              │
│  - Timeout（超时）                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────┐    ┌─────────────────────┐
│   Primary Service   │    │  Fallback Service   │
│   (主服务)          │    │  (降级服务)         │
│                     │    │                     │
│  ElasticsearchSearch│    │  DatabaseSearch     │
│  RabbitMqPublisher  │    │  InMemoryPublisher  │
└─────────────────────┘    └─────────────────────┘
```

### 降级场景

| 服务 | 主服务 | 降级服务 | 触发条件 |
|------|--------|---------|---------|
| 搜索 | Elasticsearch | Database | ES 连接失败或超时 |
| 事件发布 | RabbitMQ | InProcess | MQ 连接失败 |
| 缓存读取 | Redis | Database | Redis 连接失败 |
| 热度计算 | TieredHotness | LazyHotness | 计算超时 |
| 通知推送 | SignalR | 数据库存储 | WebSocket 断开 |


## 外部服务集成

### im-system 集成

博客系统集成了独立的 **im-system**（即时通讯系统）作为私信功能的底层基础设施。

```
┌─────────────────────────────────────────────────────────────┐
│                      ZhiCore System                             │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              ZhiCore-message                            │   │
│  │  (博客消息业务编排层)                                 │   │
│  │                                                      │   │
│  │  ✓ 系统通知 (点赞、评论、关注)                       │   │
│  │  ✓ 私信业务编排 (调用 im-system)                    │   │
│  │  ✓ 消息聚合展示                                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │ Feign Client                     │
└──────────────────────────┼───────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    im-system                                 │
│              (通用 IM 基础设施)                               │
│                                                              │
│  ✓ 单聊/群聊                                                 │
│  ✓ 消息持久化                                                │
│  ✓ 在线状态                                                  │
│  ✓ 消息推送                                                  │
└─────────────────────────────────────────────────────────────┘
```

**职责划分**：

| 模块 | 职责 |
|------|------|
| **ZhiCore-message** | 系统通知管理、私信业务编排、消息权限控制、内容过滤 |
| **im-system** | 消息传输、消息持久化、在线状态、消息推送 |

**详细文档**：[ZhiCore-message 与 im-system 集成架构](../architecture/ZhiCore-message-im-integration.md)
