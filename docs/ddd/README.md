# DDD 重构设计文档

本目录包含博客系统 DDD（领域驱动设计）重构的详细设计文档，按限界上下文（Bounded Context）组织。

## 文档完成状态

| 上下文 | README | 详细文档 | 状态 |
|--------|--------|---------|------|
| Post Context | ✅ | 5/5 | 完成 |
| User Context | ✅ | 4/4 | 完成 |
| Comment Context | ✅ | 3/3 | 完成 |
| Social Context | ✅ | 3/3 | 完成 |
| Ranking Context | ✅ | 3/3 | 完成 |
| Content Context | ✅ | 2/2 | 完成 |
| Auth Context | ✅ | 3/3 | 完成 |
| Admin Context | ✅ | 1/1 | 完成 |
| Search Context | ✅ | 1/1 | 完成 |
| Infrastructure | ✅ | 3/3 | 完成 |

## 目录结构

```
docs/ddd/
├── README.md                    # 本文件
├── overview.md                  # 架构总览
├── post-context/                # 文章上下文 ✅
│   ├── README.md                ✅
│   ├── post-service.md          ✅ 文章服务
│   ├── post-like-service.md     ✅ 文章点赞服务
│   ├── post-favorite-service.md ✅ 文章收藏服务
│   ├── post-stats-service.md    ✅ 文章统计服务
│   └── view-count-service.md    ✅ 阅读量服务
├── user-context/                # 用户上下文 ✅
│   ├── README.md                ✅
│   ├── user-service.md          ✅ 用户服务
│   ├── user-follow-service.md   ✅ 用户关注服务
│   ├── user-block-service.md    ✅ 用户拉黑服务
│   └── session-service.md       ✅ 会话服务
├── comment-context/             # 评论上下文 ✅
│   ├── README.md                ✅
│   ├── comment-service.md       ✅ 评论服务
│   ├── comment-like-service.md  ✅ 评论点赞服务
│   └── comment-stats-service.md ✅ 评论统计服务
├── social-context/              # 社交上下文 ✅
│   ├── README.md                ✅
│   ├── notification-service.md  ✅ 通知服务
│   ├── message-service.md       ✅ 私信服务
│   └── chat-service.md          ✅ 聊天服务
├── ranking-context/             # 排名上下文 ✅
│   ├── README.md                ✅
│   ├── post-hotness-service.md  ✅ 文章热度服务
│   ├── creator-hotness-service.md ✅ 创作者热度服务
│   └── ranking-service.md       ✅ 排行榜服务
├── content-context/             # 内容上下文 ✅
│   ├── README.md                ✅
│   ├── topic-service.md         ✅ 话题服务
│   └── report-service.md        ✅ 举报服务
├── auth-context/                # 认证上下文 ✅
│   ├── README.md                ✅
│   ├── auth-service.md          ✅ 认证服务
│   ├── anti-spam-service.md     ✅ 防刷服务
│   └── jwt-service.md           ✅ JWT服务
├── admin-context/               # 管理上下文 ✅
│   ├── README.md                ✅
│   └── admin-services.md        ✅ 管理服务
├── search-context/              # 搜索上下文 ✅
│   ├── README.md                ✅
│   └── search-service.md        ✅ 搜索服务
└── infrastructure/              # 基础设施 ✅
    ├── README.md                ✅
    ├── caching.md               ✅ 缓存策略
    ├── resilience.md            ✅ 服务降级
    └── events.md                ✅ 领域事件
```

图例：✅ 已完成 | ⏳ 待完善

## 文档约定

每个服务文档包含以下部分：

1. **服务概述** - 服务的职责和边界
2. **当前实现** - 现有代码的依赖和问题
3. **DDD 重构设计** - 重构后的分层设计
4. **领域事件** - 服务发布和订阅的事件
5. **缓存策略** - Redis 缓存设计
6. **降级策略** - 服务不可用时的降级方案
7. **接口定义** - Repository、Domain Service、Application Service 接口

## 核心概念

### 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Presentation Layer                            │
│                    (Controllers, Hubs)                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Application Layer                             │
│              (Application Services, DTOs)                        │
│              协调领域对象完成用例                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Domain Layer                                │
│         (Entities, Domain Services, Domain Events)               │
│              核心业务逻辑和规则                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                           │
│            (Repositories, External Services)                     │
│              数据访问和外部服务集成                                │
└─────────────────────────────────────────────────────────────────┘
```

### 领域事件流

```
业务操作 → Application Service → 发布领域事件
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
              EventHandler1     EventHandler2     EventHandler3
              (通知服务)        (热度更新)        (搜索索引)
```

## 相关文档

- [需求文档](../../.kiro/specs/ddd-lite-refactor/requirements.md)
- [设计文档](../../.kiro/specs/ddd-lite-refactor/design.md)
- [任务清单](../../.kiro/specs/ddd-lite-refactor/tasks.md)
- [数据库设计文档](../database/README.md)
