# NotificationService 详细设计

## 服务概述

NotificationService 负责通知管理，包括：
- 通知创建（多种类型）
- 通知查询（分页、未读数）
- 通知状态管理（已读/未读）
- 实时推送（通过 SignalR）

## 当前实现分析

### 依赖清单（7 依赖）

```csharp
public class NotificationService(
    AppDbContext dbContext,
    IMapper mapper,
    ILogger<NotificationService> logger,
    IConnectionMultiplexer redis,
    IEventPublisher eventPublisher,
    IRabbitMqPolicyProvider rabbitMqPolicyProvider,
    IHubContext<NotificationHub> hubContext
) : INotificationService
```

### 当前架构特点

1. **多渠道推送**：数据库持久化 + SignalR 实时推送
2. **MQ 异步处理**：通过 RabbitMQ 异步创建通知
3. **未读数缓存**：Redis 缓存未读通知数量

### 问题分析

1. **被动调用**：其他服务直接调用 NotificationService
2. **耦合严重**：PostService、CommentService 等都依赖 NotificationService
3. **职责混乱**：既处理业务逻辑又处理推送

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/INotificationRepository.cs
public interface INotificationRepository
{
    /// <summary>
    /// 根据ID获取通知
    /// </summary>
    Task<Notification?> GetByIdAsync(long notificationId);
    
    /// <summary>
    /// 获取用户通知列表
    /// </summary>
    Task<(IReadOnlyList<NotificationVo> Items, int Total)> GetByUserIdAsync(
        string userId, int page, int pageSize, bool? isRead = null);
    
    /// <summary>
    /// 添加通知
    /// </summary>
    Task<Notification> AddAsync(Notification notification);
    
    /// <summary>
    /// 批量添加通知
    /// </summary>
    Task AddBatchAsync(IEnumerable<Notification> notifications);
    
    /// <summary>
    /// 标记为已读
    /// </summary>
    Task MarkAsReadAsync(long notificationId, string userId);
    
    /// <summary>
    /// 标记所有为已读
    /// </summary>
    Task MarkAllAsReadAsync(string userId);
    
    /// <summary>
    /// 获取未读数量
    /// </summary>
    Task<int> GetUnreadCountAsync(string userId);
    
    /// <summary>
    /// 删除通知
    /// </summary>
    Task DeleteAsync(long notificationId, string userId);
    
    /// <summary>
    /// 清空所有通知
    /// </summary>
    Task ClearAllAsync(string userId);
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Social/INotificationApplicationService.cs
public interface INotificationApplicationService
{
    Task<NotificationVo> CreateNotificationAsync(CreateNotificationReq req);
    Task<PaginatedResult<NotificationVo>> GetNotificationsAsync(string userId, int page, int pageSize);
    Task<int> GetUnreadCountAsync(string userId);
    Task MarkAsReadAsync(string userId, long notificationId);
    Task MarkAllAsReadAsync(string userId);
    Task DeleteNotificationAsync(string userId, long notificationId);
}

// ZhiCoreCore/Application/Social/NotificationApplicationService.cs
public class NotificationApplicationService : INotificationApplicationService
{
    private readonly INotificationRepository _notificationRepository;
    private readonly ISnowflakeIdService _snowflakeIdService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<NotificationApplicationService> _logger;
    
    public async Task<NotificationVo> CreateNotificationAsync(CreateNotificationReq req)
    {
        var notification = new Notification
        {
            Id = _snowflakeIdService.NextId(),
            UserId = req.UserId,
            Type = req.Type,
            Title = req.Title,
            Content = req.Content,
            SourceUserId = req.SourceUserId,
            SourceUserName = req.SourceUserName,
            TargetId = req.TargetId,
            TargetType = req.TargetType,
            IsRead = false,
            CreateTime = DateTimeOffset.UtcNow
        };
        
        await _notificationRepository.AddAsync(notification);
        
        // 发布事件用于实时推送
        await _eventDispatcher.DispatchAsync(new NotificationCreatedEvent
        {
            NotificationId = notification.Id,
            UserId = req.UserId,
            Type = req.Type,
            Title = req.Title
        });
        
        _logger.LogDebug("通知创建成功: NotificationId={NotificationId}, UserId={UserId}", 
            notification.Id, req.UserId);
        
        return MapToVo(notification);
    }
    
    public async Task<int> GetUnreadCountAsync(string userId)
    {
        return await _notificationRepository.GetUnreadCountAsync(userId);
    }
    
    public async Task MarkAsReadAsync(string userId, long notificationId)
    {
        await _notificationRepository.MarkAsReadAsync(notificationId, userId);
        
        // 更新未读数缓存
        await _eventDispatcher.DispatchAsync(new NotificationReadEvent
        {
            UserId = userId,
            NotificationId = notificationId
        });
    }
}
```

## 领域事件

### 订阅的事件（来自其他上下文）

| 事件 | 来源 | 处理逻辑 |
|------|------|---------|
| PostLikedEvent | Post Context | 创建点赞通知 |
| PostFavoritedEvent | Post Context | 创建收藏通知 |
| CommentCreatedEvent | Comment Context | 创建评论通知 |
| CommentLikedEvent | Comment Context | 创建评论点赞通知 |
| UserFollowedEvent | User Context | 创建关注通知 |
| MessageSentEvent | Social Context | 创建私信通知 |

### 发布的事件

| 事件 | 触发场景 | 处理器 |
|------|---------|--------|
| NotificationCreatedEvent | 通知创建 | SignalRPushHandler |
| NotificationReadEvent | 通知已读 | UnreadCountCacheHandler |

### 事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/Social/PostLikedNotificationHandler.cs
public class PostLikedNotificationHandler : IDomainEventHandler<PostLikedEvent>
{
    private readonly INotificationApplicationService _notificationService;
    private readonly ILogger<PostLikedNotificationHandler> _logger;
    
    public async Task HandleAsync(PostLikedEvent @event, CancellationToken ct = default)
    {
        // 不给自己发通知
        if (@event.UserId == @event.PostOwnerId) return;
        
        // 取消点赞不发通知
        if (!@event.IsLike) return;
        
        try
        {
            await _notificationService.CreateNotificationAsync(new CreateNotificationReq
            {
                UserId = @event.PostOwnerId,
                Type = NotificationType.PostLiked,
                Title = $"{@event.UserNickName} 赞了你的文章",
                Content = @event.PostTitle,
                SourceUserId = @event.UserId,
                SourceUserName = @event.UserNickName,
                TargetId = @event.PostId.ToString(),
                TargetType = "post"
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "创建点赞通知失败: PostId={PostId}", @event.PostId);
        }
    }
}

// ZhiCoreCore/Domain/EventHandlers/Social/NotificationCreatedEventHandler.cs
public class NotificationCreatedEventHandler : IDomainEventHandler<NotificationCreatedEvent>
{
    private readonly IHubContext<NotificationHub> _hubContext;
    private readonly IDatabase _redis;
    private readonly ILogger<NotificationCreatedEventHandler> _logger;
    
    public async Task HandleAsync(NotificationCreatedEvent @event, CancellationToken ct = default)
    {
        // 1. 通过 SignalR 实时推送
        try
        {
            await _hubContext.Clients
                .User(@event.UserId)
                .SendAsync("ReceiveNotification", new
                {
                    @event.NotificationId,
                    @event.Type,
                    @event.Title
                }, ct);
            
            _logger.LogDebug("通知已推送: UserId={UserId}", @event.UserId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "推送通知失败: UserId={UserId}", @event.UserId);
        }
        
        // 2. 更新未读数缓存
        try
        {
            var key = $"notification:unread:{@event.UserId}";
            await _redis.StringIncrementAsync(key);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新未读数缓存失败: UserId={UserId}", @event.UserId);
        }
    }
}
```

## 通知类型

```csharp
public enum NotificationType
{
    // 文章相关
    PostLiked = 1,        // 文章被点赞
    PostFavorited = 2,    // 文章被收藏
    PostCommented = 3,    // 文章被评论
    
    // 评论相关
    CommentReplied = 10,  // 评论被回复
    CommentLiked = 11,    // 评论被点赞
    
    // 用户相关
    Followed = 20,        // 被关注
    
    // 私信相关
    MessageReceived = 30, // 收到私信
    
    // 系统通知
    SystemNotice = 100,   // 系统通知
    AdminNotice = 101     // 管理员通知
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `notification:unread:{userId}` | String | 未读通知数 | 永久 |
| `notification:list:{userId}:{page}` | String | 通知列表缓存 | 5 分钟 |

### 未读数缓存

```csharp
public async Task<int> GetUnreadCountAsync(string userId)
{
    var cacheKey = $"notification:unread:{userId}";
    
    // 尝试从缓存获取
    var cached = await _redis.StringGetAsync(cacheKey);
    if (cached.HasValue && int.TryParse(cached, out var count))
    {
        return count;
    }
    
    // 缓存未命中，从数据库获取
    var dbCount = await _notificationRepository.GetUnreadCountAsync(userId);
    
    // 写入缓存
    await _redis.StringSetAsync(cacheKey, dbCount.ToString());
    
    return dbCount;
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 7 | 4 |
| 被调用方式 | 直接调用 | 事件驱动 |
| 耦合度 | 高（被多个服务依赖） | 低（通过事件解耦） |
| 推送逻辑 | 混在服务中 | 独立事件处理器 |
