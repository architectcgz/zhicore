# Social Context（社交上下文）

## 概述

社交上下文负责用户间的社交互动功能，包括通知、私信等。

> **重要变更**：私信功能已迁移到独立的 **im-system** 服务。blog-message 模块现在作为"博客消息业务编排层"，负责系统通知管理和私信业务编排。详见 [blog-message 与 im-system 集成架构](../../architecture/blog-message-im-integration.md)。

## 服务清单

| 服务 | 接口 | 主要职责 | 状态 |
|------|------|---------|------|
| NotificationService | INotificationService | 系统通知管理 | ✅ 活跃 |
| MessageService | IMessageService | 私信业务编排（调用 im-system） | 🔄 重构中 |
| ChatService | IChatService | 聊天管理（已废弃） | ⚠️ 待移除 |
| AssistantMessageService | IAssistantMessageService | AI 助手消息 | ✅ 活跃 |
| CachedChatService | IChatService | 聊天缓存装饰器（已废弃） | ⚠️ 待移除 |

## 领域事件

### 发布的事件

| 事件 | 触发场景 | 处理器 | 说明 |
|------|---------|--------|------|
| MessageSentEvent | 私信发送 | NotificationHandler | ⚠️ 将由 im-system 发布 |
| NotificationCreatedEvent | 通知创建 | SignalRHandler | ✅ 活跃 |

### 订阅的事件

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| PostLikedEvent | Post Context | 创建点赞通知 |
| PostFavoritedEvent | Post Context | 创建收藏通知 |
| CommentCreatedEvent | Comment Context | 创建评论通知 |
| UserFollowedEvent | User Context | 创建关注通知 |

## 目录结构

```
BlogCore/
├── Application/Social/
│   ├── IChatApplicationService.cs
│   ├── ChatApplicationService.cs
│   ├── INotificationApplicationService.cs
│   └── NotificationApplicationService.cs
├── Domain/
│   ├── Repositories/
│   │   ├── IChatRepository.cs
│   │   └── INotificationRepository.cs
│   └── EventHandlers/Social/
│       ├── MessageSentEventHandler.cs
│       └── NotificationCreatedEventHandler.cs
└── Infrastructure/Repositories/
    ├── ChatRepository.cs
    └── NotificationRepository.cs
```

## 通知类型

| 类型 | 触发事件 | 通知内容 | 存储位置 |
|------|---------|---------|---------|
| PostLiked | PostLikedEvent | "xxx 赞了你的文章《xxx》" | blog-message |
| PostFavorited | PostFavoritedEvent | "xxx 收藏了你的文章《xxx》" | blog-message |
| PostCommented | CommentCreatedEvent | "xxx 评论了你的文章《xxx》" | blog-message |
| CommentReplied | CommentCreatedEvent | "xxx 回复了你的评论" | blog-message |
| CommentLiked | CommentLikedEvent | "xxx 赞了你的评论" | blog-message |
| Followed | UserFollowedEvent | "xxx 关注了你" | blog-message |
| MessageReceived | - | "xxx 给你发送了私信" | im-system |

> **注意**：私信通知（MessageReceived）现在由 im-system 管理，不再存储在 blog-message 的系统通知表中。

## 实时推送

通知创建后通过 SignalR 实时推送到客户端：

```csharp
public class NotificationCreatedEventHandler : IDomainEventHandler<NotificationCreatedEvent>
{
    private readonly IHubContext<NotificationHub> _hubContext;
    
    public async Task HandleAsync(NotificationCreatedEvent @event, CancellationToken ct)
    {
        await _hubContext.Clients
            .User(@event.UserId)
            .SendAsync("ReceiveNotification", new
            {
                @event.NotificationId,
                @event.NotificationType,
                @event.Title
            }, ct);
    }
}
```

## 详细文档

- [NotificationService 详细设计](./notification-service.md) ✅
- [MessageService 详细设计](./message-service.md) 🔄 需要更新（im-system 集成）
- [ChatService 详细设计](./chat-service.md) ⚠️ 已废弃

## 架构变更

### im-system 集成

私信功能已从 blog-message 迁移到独立的 im-system 服务：

```
┌─────────────────────────────────────────────────────────────┐
│                      blog-message                            │
│              (博客消息业务编排层)                             │
│                                                              │
│  ✓ 系统通知管理 (点赞、评论、关注)                           │
│  ✓ 私信业务编排 (权限控制、内容过滤)                         │
│  ✓ 消息聚合展示                                              │
└──────────────────────────┬───────────────────────────────────┘
                           │ Feign Client
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    im-system                                 │
│              (通用 IM 基础设施)                               │
│                                                              │
│  ✓ 消息传输与推送                                            │
│  ✓ 消息持久化                                                │
│  ✓ 在线状态管理                                              │
└─────────────────────────────────────────────────────────────┘
```

**详细文档**：[blog-message 与 im-system 集成架构](../../architecture/blog-message-im-integration.md)
