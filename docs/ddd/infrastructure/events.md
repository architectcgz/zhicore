# 领域事件

## 概述

领域事件是 DDD 中解耦限界上下文的核心机制。当一个上下文中发生重要业务事件时，通过发布领域事件通知其他上下文，而不是直接调用。

## 事件分发模式

### InProcess（进程内分发）

- **默认模式**，不依赖外部 MQ
- 同步执行，事务内处理
- 适用于单体应用或微服务内部事件

### RabbitMQ（消息队列分发）

- **可选模式**，用于跨进程通信
- 异步执行，最终一致性
- 适用于微服务间通信

```
┌─────────────────────────────────────────────────────────────────┐
│                     Application Service                          │
│                                                                  │
│  await _eventDispatcher.DispatchAsync(new PostPublishedEvent()); │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   IDomainEventDispatcher                         │
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐             │
│  │  InProcess (默认)   │    │  RabbitMQ (可选)    │             │
│  │                     │    │                     │             │
│  │  同步分发到         │    │  发布到 MQ          │             │
│  │  进程内 Handler     │    │  由 Consumer 处理   │             │
│  └─────────────────────┘    └─────────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

## 基础接口

### IDomainEvent

```csharp
// ZhiCoreShared/Messages/DomainEvents/IDomainEvent.cs
public interface IDomainEvent
{
    /// <summary>
    /// 事件唯一标识，用于幂等处理
    /// </summary>
    string EventId { get; }
    
    /// <summary>
    /// 事件发生时间
    /// </summary>
    DateTimeOffset OccurredAt { get; }
    
    /// <summary>
    /// 事件类型名称
    /// </summary>
    string EventType { get; }
}
```

### DomainEventBase

```csharp
// ZhiCoreShared/Messages/DomainEvents/DomainEventBase.cs
public abstract record DomainEventBase : IDomainEvent
{
    public string EventId { get; init; } = Guid.NewGuid().ToString();
    public DateTimeOffset OccurredAt { get; init; } = DateTimeOffset.UtcNow;
    public abstract string EventType { get; }
}
```

### IDomainEventDispatcher

```csharp
// ZhiCoreCore/Domain/Events/IDomainEventDispatcher.cs
public interface IDomainEventDispatcher
{
    /// <summary>
    /// 分发单个领域事件
    /// </summary>
    Task DispatchAsync<TEvent>(TEvent domainEvent, CancellationToken ct = default) 
        where TEvent : IDomainEvent;
    
    /// <summary>
    /// 批量分发领域事件
    /// </summary>
    Task DispatchAsync(IEnumerable<IDomainEvent> domainEvents, CancellationToken ct = default);
}
```

### IDomainEventHandler

```csharp
// ZhiCoreCore/Domain/Events/IDomainEventHandler.cs
public interface IDomainEventHandler<in TEvent> where TEvent : IDomainEvent
{
    Task HandleAsync(TEvent domainEvent, CancellationToken ct = default);
}
```

## 进程内分发器

```csharp
// ZhiCoreCore/Infrastructure/Events/InProcessDomainEventDispatcher.cs
/// <summary>
/// 进程内领域事件分发器
/// 使用 MediatR 模式，在同一进程内同步处理事件
/// </summary>
public class InProcessDomainEventDispatcher : IDomainEventDispatcher
{
    private readonly IServiceProvider _serviceProvider;
    private readonly ILogger<InProcessDomainEventDispatcher> _logger;
    
    public InProcessDomainEventDispatcher(
        IServiceProvider serviceProvider,
        ILogger<InProcessDomainEventDispatcher> logger)
    {
        _serviceProvider = serviceProvider;
        _logger = logger;
    }
    
    public async Task DispatchAsync<TEvent>(TEvent domainEvent, CancellationToken ct) 
        where TEvent : IDomainEvent
    {
        var eventType = domainEvent.GetType();
        var handlerType = typeof(IDomainEventHandler<>).MakeGenericType(eventType);
        var handlers = _serviceProvider.GetServices(handlerType);
        
        _logger.LogDebug("分发事件 {EventType}，找到 {HandlerCount} 个处理器",
            domainEvent.EventType, handlers.Count());
        
        foreach (var handler in handlers)
        {
            try
            {
                var method = handlerType.GetMethod("HandleAsync");
                var task = (Task)method!.Invoke(handler, new object[] { domainEvent, ct })!;
                await task;
                
                _logger.LogDebug("事件处理成功: {EventType}, Handler={Handler}",
                    domainEvent.EventType, handler.GetType().Name);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "处理领域事件失败: {EventType}, Handler={Handler}, EventId={EventId}",
                    domainEvent.EventType, handler.GetType().Name, domainEvent.EventId);
                // 继续处理其他 handler，不中断
            }
        }
    }
    
    public async Task DispatchAsync(IEnumerable<IDomainEvent> domainEvents, CancellationToken ct)
    {
        foreach (var domainEvent in domainEvents)
        {
            await DispatchAsync(domainEvent, ct);
        }
    }
}
```

## RabbitMQ 分发器

```csharp
// ZhiCoreCore/Infrastructure/Events/RabbitMqDomainEventDispatcher.cs
/// <summary>
/// RabbitMQ 领域事件分发器
/// 用于需要跨进程通信的场景
/// </summary>
public class RabbitMqDomainEventDispatcher : IDomainEventDispatcher
{
    private readonly IEventPublisher _eventPublisher;
    private readonly ILogger<RabbitMqDomainEventDispatcher> _logger;
    
    public RabbitMqDomainEventDispatcher(
        IEventPublisher eventPublisher,
        ILogger<RabbitMqDomainEventDispatcher> logger)
    {
        _eventPublisher = eventPublisher;
        _logger = logger;
    }
    
    public async Task DispatchAsync<TEvent>(TEvent domainEvent, CancellationToken ct) 
        where TEvent : IDomainEvent
    {
        try
        {
            await _eventPublisher.PublishDomainEventAsync(domainEvent, ct);
            _logger.LogDebug("领域事件已发布到 RabbitMQ: {EventType}, EventId={EventId}",
                domainEvent.EventType, domainEvent.EventId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "发布领域事件到 RabbitMQ 失败: {EventType}, EventId={EventId}",
                domainEvent.EventType, domainEvent.EventId);
            throw;
        }
    }
    
    public async Task DispatchAsync(IEnumerable<IDomainEvent> domainEvents, CancellationToken ct)
    {
        foreach (var domainEvent in domainEvents)
        {
            await DispatchAsync(domainEvent, ct);
        }
    }
}
```

## 事件定义

### Post Context 事件

```csharp
// ZhiCoreShared/Messages/DomainEvents/PostEvents.cs
public record PostPublishedEvent : DomainEventBase
{
    public override string EventType => nameof(PostPublishedEvent);
    
    public long PostId { get; init; }
    public string AuthorId { get; init; } = string.Empty;
    public string Title { get; init; } = string.Empty;
    public string? Excerpt { get; init; }
    public long? TopicId { get; init; }
    public DateTimeOffset PublishedAt { get; init; }
}

public record PostLikedEvent : DomainEventBase
{
    public override string EventType => nameof(PostLikedEvent);
    
    public long PostId { get; init; }
    public string UserId { get; init; } = string.Empty;
    public string AuthorId { get; init; } = string.Empty;
    public string PostTitle { get; init; } = string.Empty;
    public bool IsLike { get; init; }
}

public record PostViewedEvent : DomainEventBase
{
    public override string EventType => nameof(PostViewedEvent);
    
    public long PostId { get; init; }
    public string? UserId { get; init; }
    public string? ClientIp { get; init; }
}
```

### Comment Context 事件

```csharp
// ZhiCoreShared/Messages/DomainEvents/CommentEvents.cs
public record CommentCreatedEvent : DomainEventBase
{
    public override string EventType => nameof(CommentCreatedEvent);
    
    public long CommentId { get; init; }
    public long PostId { get; init; }
    public string PostTitle { get; init; } = string.Empty;
    public string AuthorId { get; init; } = string.Empty;
    public string PostOwnerId { get; init; } = string.Empty;
    public long? ParentCommentId { get; init; }
    public string? ParentCommentAuthorId { get; init; }
    public string Content { get; init; } = string.Empty;
}

public record CommentDeletedEvent : DomainEventBase
{
    public override string EventType => nameof(CommentDeletedEvent);
    
    public long CommentId { get; init; }
    public long PostId { get; init; }
    public int DecrementCount { get; init; } = 1;
}
```

### User Context 事件

```csharp
// ZhiCoreShared/Messages/DomainEvents/UserEvents.cs
public record UserFollowedEvent : DomainEventBase
{
    public override string EventType => nameof(UserFollowedEvent);
    
    public string FollowerId { get; init; } = string.Empty;
    public string FollowerNickName { get; init; } = string.Empty;
    public string FollowingId { get; init; } = string.Empty;
}

public record UserProfileUpdatedEvent : DomainEventBase
{
    public override string EventType => nameof(UserProfileUpdatedEvent);
    
    public string UserId { get; init; } = string.Empty;
    public string? NickName { get; init; }
    public string? AvatarUrl { get; init; }
}
```

## 事件处理器

### PostPublishedEventHandler

```csharp
// ZhiCoreCore/Domain/EventHandlers/Post/PostPublishedEventHandler.cs
public class PostPublishedEventHandler : IDomainEventHandler<PostPublishedEvent>
{
    private readonly IPostHotnessService _hotnessService;
    private readonly ISearchService _searchService;
    private readonly ILogger<PostPublishedEventHandler> _logger;
    
    public PostPublishedEventHandler(
        IPostHotnessService hotnessService,
        ISearchService searchService,
        ILogger<PostPublishedEventHandler> logger)
    {
        _hotnessService = hotnessService;
        _searchService = searchService;
        _logger = logger;
    }
    
    public async Task HandleAsync(PostPublishedEvent @event, CancellationToken ct = default)
    {
        // 1. 初始化文章热度
        try
        {
            await _hotnessService.InitializePostHotnessAsync(@event.PostId);
            _logger.LogDebug("文章热度已初始化: PostId={PostId}", @event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "初始化文章热度失败: PostId={PostId}", @event.PostId);
        }
        
        // 2. 索引到搜索引擎
        try
        {
            await _searchService.IndexPostAsync(@event.PostId);
            _logger.LogDebug("文章已索引: PostId={PostId}", @event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "索引文章失败: PostId={PostId}", @event.PostId);
        }
    }
}
```

### CommentCreatedEventHandler

```csharp
// ZhiCoreCore/Domain/EventHandlers/Comment/CommentCreatedEventHandler.cs
public class CommentCreatedEventHandler : IDomainEventHandler<CommentCreatedEvent>
{
    private readonly IPostStatsRepository _postStatsRepository;
    private readonly INotificationService _notificationService;
    private readonly ILogger<CommentCreatedEventHandler> _logger;
    
    public async Task HandleAsync(CommentCreatedEvent @event, CancellationToken ct = default)
    {
        // 1. 更新文章评论数
        try
        {
            await _postStatsRepository.IncrementCommentCountAsync(@event.PostId, 1);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新文章评论数失败: PostId={PostId}", @event.PostId);
        }
        
        // 2. 发送通知
        try
        {
            if (@event.ParentCommentAuthorId != null && @event.AuthorId != @event.ParentCommentAuthorId)
            {
                await _notificationService.NotifyCommentRepliedAsync(
                    @event.ParentCommentAuthorId,
                    @event.PostId,
                    @event.PostTitle,
                    "用户",
                    @event.Content,
                    @event.AuthorId,
                    @event.CommentId,
                    @event.ParentCommentId);
            }
            else if (@event.AuthorId != @event.PostOwnerId)
            {
                await _notificationService.NotifyPostCommentedAsync(
                    @event.PostOwnerId,
                    @event.PostId,
                    @event.PostTitle,
                    "用户",
                    @event.Content,
                    @event.AuthorId,
                    @event.CommentId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "发送评论通知失败: CommentId={CommentId}", @event.CommentId);
        }
    }
}
```

## 幂等性处理

### 已处理事件存储

```csharp
// ZhiCoreCore/Domain/Events/IProcessedEventStore.cs
public interface IProcessedEventStore
{
    Task<bool> IsProcessedAsync(string eventId);
    Task MarkAsProcessedAsync(string eventId);
}

// ZhiCoreCore/Infrastructure/Events/RedisProcessedEventStore.cs
public class RedisProcessedEventStore : IProcessedEventStore
{
    private readonly IDatabase _redis;
    private readonly TimeSpan _ttl = TimeSpan.FromDays(7);
    
    public RedisProcessedEventStore(IConnectionMultiplexer redis)
    {
        _redis = redis.GetDatabase();
    }
    
    public async Task<bool> IsProcessedAsync(string eventId)
    {
        var key = $"processed_event:{eventId}";
        return await _redis.KeyExistsAsync(key);
    }
    
    public async Task MarkAsProcessedAsync(string eventId)
    {
        var key = $"processed_event:{eventId}";
        await _redis.StringSetAsync(key, "1", _ttl);
    }
}
```

### 幂等装饰器

```csharp
// ZhiCoreCore/Domain/EventHandlers/IdempotentEventHandlerDecorator.cs
public class IdempotentEventHandlerDecorator<TEvent> : IDomainEventHandler<TEvent> 
    where TEvent : IDomainEvent
{
    private readonly IDomainEventHandler<TEvent> _inner;
    private readonly IProcessedEventStore _processedEventStore;
    private readonly ILogger _logger;
    
    public IdempotentEventHandlerDecorator(
        IDomainEventHandler<TEvent> inner,
        IProcessedEventStore processedEventStore,
        ILogger<IdempotentEventHandlerDecorator<TEvent>> logger)
    {
        _inner = inner;
        _processedEventStore = processedEventStore;
        _logger = logger;
    }
    
    public async Task HandleAsync(TEvent domainEvent, CancellationToken ct = default)
    {
        // 检查事件是否已处理
        if (await _processedEventStore.IsProcessedAsync(domainEvent.EventId))
        {
            _logger.LogDebug("事件已处理，跳过: {EventId}", domainEvent.EventId);
            return;
        }
        
        // 处理事件
        await _inner.HandleAsync(domainEvent, ct);
        
        // 标记为已处理
        await _processedEventStore.MarkAsProcessedAsync(domainEvent.EventId);
    }
}
```

## DI 注册

```csharp
// ZhiCoreCore/Extensions/DomainEventServiceExtensions.cs
public static class DomainEventServiceExtensions
{
    public static IServiceCollection AddDomainEvents(this IServiceCollection services, DomainEventConfig config)
    {
        // 注册事件分发器
        if (config.DispatchMode == EventDispatchMode.InProcess)
        {
            services.AddScoped<IDomainEventDispatcher, InProcessDomainEventDispatcher>();
        }
        else
        {
            services.AddScoped<IDomainEventDispatcher, RabbitMqDomainEventDispatcher>();
        }
        
        // 注册幂等存储
        services.AddSingleton<IProcessedEventStore, RedisProcessedEventStore>();
        
        // 注册事件处理器
        services.AddScoped<IDomainEventHandler<PostPublishedEvent>, PostPublishedEventHandler>();
        services.AddScoped<IDomainEventHandler<PostLikedEvent>, PostLikedEventHandler>();
        services.AddScoped<IDomainEventHandler<CommentCreatedEvent>, CommentCreatedEventHandler>();
        services.AddScoped<IDomainEventHandler<UserFollowedEvent>, UserFollowedEventHandler>();
        // ... 其他处理器
        
        return services;
    }
}
```

## 配置

```csharp
// ZhiCoreCore/Config/DomainEventConfig.cs
public class DomainEventConfig
{
    /// <summary>
    /// 事件分发模式
    /// </summary>
    public EventDispatchMode DispatchMode { get; set; } = EventDispatchMode.InProcess;
}

public enum EventDispatchMode
{
    /// <summary>
    /// 进程内同步分发
    /// </summary>
    InProcess,
    
    /// <summary>
    /// 通过 RabbitMQ 异步分发
    /// </summary>
    RabbitMQ
}
```

```json
// appsettings.json
{
  "DomainEvent": {
    "DispatchMode": "InProcess"
  }
}
```
