# User Context（用户上下文）

## 概述

用户上下文负责用户相关的所有功能，包括用户信息管理、关注关系、拉黑功能、会话管理等。

## 聚合根

### UserAggregate

```
UserAggregate
├── User（聚合根）
│   ├── Id: string (GUID)
│   ├── UserName: string
│   ├── NickName: string
│   ├── Email: string
│   ├── AvatarUrl: string
│   ├── Bio: string
│   └── Status: UserStatus
└── UserRole（关联实体）
    ├── UserId: string
    └── RoleId: int
```

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| UserService | IUserService | 2 | 用户信息 CRUD |
| UserFollowService | IUserFollowService | 8 | 用户关注/取消关注 |
| UserBlockService | IUserBlockService | 3 | 用户拉黑/取消拉黑 |
| SessionService | ISessionService | 3 | 用户会话管理 |
| UserRoleService | IUserRoleService | 1 | 用户角色管理 |
| UserActivityService | IUserActivityService | 3 | 用户活跃度统计 |
| UserCheckInService | IUserCheckInService | 3 | 用户签到 |
| HistoryService | IHistoryService | 2 | 用户历史记录 |
| CachedUserService | IUserService | 4 | 用户缓存装饰器 |
| CachedUserFollowService | IUserFollowService | 4 | 关注缓存装饰器 |
| CachedUserBlockService | IUserBlockService | 4 | 拉黑缓存装饰器 |
| CachedSessionService | ISessionService | 4 | 会话缓存装饰器 |

## 领域事件

### 发布的事件

| 事件 | 触发场景 | 处理器 |
|------|---------|--------|
| UserFollowedEvent | 用户关注 | NotificationHandler, RankingHandler |
| UserUnfollowedEvent | 取消关注 | RankingHandler |
| UserProfileUpdatedEvent | 更新资料 | PostCacheHandler, CommentCacheHandler |
| UserBlockedEvent | 用户拉黑 | FollowHandler |

### 订阅的事件

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| PostPublishedEvent | Post Context | 更新用户发文统计 |
| CommentCreatedEvent | Comment Context | 更新用户评论统计 |

## 目录结构

```
BlogCore/
├── Application/User/
│   ├── IUserApplicationService.cs
│   ├── UserApplicationService.cs
│   ├── IUserFollowApplicationService.cs
│   ├── UserFollowApplicationService.cs
│   ├── IUserBlockApplicationService.cs
│   └── UserBlockApplicationService.cs
├── Domain/
│   ├── Repositories/
│   │   ├── IUserRepository.cs
│   │   ├── IUserFollowRepository.cs
│   │   └── IUserBlockRepository.cs
│   ├── Services/
│   │   ├── IUserFollowDomainService.cs
│   │   └── UserFollowDomainService.cs
│   └── EventHandlers/User/
│       ├── UserFollowedEventHandler.cs
│       ├── UserProfileUpdatedEventHandler.cs
│       └── UserBlockedEventHandler.cs
└── Infrastructure/Repositories/
    ├── UserRepository.cs
    ├── CachedUserRepository.cs
    ├── UserFollowRepository.cs
    └── UserBlockRepository.cs
```

## 详细文档

- [UserService 详细设计](./user-service.md) ✅
- [UserFollowService 详细设计](./user-follow-service.md) ✅
- [UserBlockService 详细设计](./user-block-service.md) ✅
- [SessionService 详细设计](./session-service.md) ✅
