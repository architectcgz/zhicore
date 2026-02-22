# CommentLikeService 详细设计

## 服务概述

CommentLikeService 负责评论点赞功能的管理，包括：
- 评论点赞（原子操作，防重复）
- 取消点赞
- 点赞状态查询（单个/批量）
- 点赞通知发送

## 当前实现分析

### 依赖清单（10 个依赖）

```csharp
public class CommentLikeService(
    AppDbContext dbContext,
    IConnectionMultiplexer redis,
    ILogger<CommentLikeService> logger,
    IServiceScopeFactory serviceScopeFactory,
    IUserBlockService userBlockService,
    IEventPublisher eventPublisher,
    ICommentCacheService commentCacheService,
    IOptions<CommentCacheConfig> config,
    IRedisPolicyProvider redisPolicyProvider,
    IRabbitMqPolicyProvider rabbitMqPolicyProvider) : ICommentLikeService
```

### 当前架构特点

1. **Redis Set 存储点赞状态**：使用 Set 天然防重复
2. **MQ 异步写库**：点赞记录通过 RabbitMQ 批量写入数据库
3. **Polly 双重降级**：Redis 和 MQ 都有降级策略
4. **异步通知**：点赞通知通过 MQ 异步发送

### 数据流

```
点赞请求
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. 验证评论存在（缓存优先）                                      │
│  2. 检查拉黑状态                                                 │
│  3. Redis Set 添加点赞状态（原子操作）                            │
│  4. 发送 MQ 消息（异步写库）                                      │
│  5. 发送通知消息（异步）                                          │
└─────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  Consumer 处理：                                                 │
│  1. 写入 CommentLike 表                                          │
│  2. 更新 CommentStats.LikeCount                                  │
│  3. 更新热度队列                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 问题分析

1. **方法过长**：LikeCommentAsync 超过 150 行
2. **降级逻辑复杂**：MQ 降级时复制了 Consumer 的完整逻辑
3. **跨上下文依赖**：通过 IServiceScopeFactory 获取 INotificationService

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/ICommentLikeRepository.cs
public interface ICommentLikeRepository
{
    /// <summary>
    /// 检查用户是否已点赞评论
    /// </summary>
    Task<bool> ExistsAsync(long commentId, string userId);
    
    /// <summary>
    /// 添加点赞记录
    /// </summary>
    Task AddAsync(CommentLike like);
    
    /// <summary>
    /// 删除点赞记录
    /// </summary>
    Task<bool> DeleteAsync(long commentId, string userId);
    
    /// <summary>
    /// 批量检查点赞状态
    /// </summary>
    Task<Dictionary<long, bool>> GetLikeStatusBatchAsync(List<long> commentIds, string userId);
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/ICommentLikeDomainService.cs
public interface ICommentLikeDomainService
{
    /// <summary>
    /// 验证点赞操作
    /// </summary>
    Task ValidateLikeAsync(long commentId, string userId);
    
    /// <summary>
    /// 检查点赞状态（Redis 优先）
    /// </summary>
    Task<bool> IsLikedAsync(long commentId, string userId);
    
    /// <summary>
    /// 添加点赞状态到缓存
    /// </summary>
    Task<bool> AddLikeToCacheAsync(long commentId, string userId);
    
    /// <summary>
    /// 从缓存移除点赞状态
    /// </summary>
    Task<bool> RemoveLikeFromCacheAsync(long commentId, string userId);
}

// ZhiCoreCore/Domain/Services/CommentLikeDomainService.cs
public class CommentLikeDomainService : ICommentLikeDomainService
{
    private readonly ICommentCacheService _commentCacheService;
    private readonly IUserBlockService _userBlockService;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly CommentCacheConfig _config;
    
    public async Task ValidateLikeAsync(long commentId, string userId)
    {
        // 1. 验证评论存在
        var comment = await _commentCacheService.GetCommentBasicInfoAsync(commentId);
        if (comment == null)
            throw new BusinessException(BusinessError.CommentNotFound);
        
        // 2. 检查拉黑状态（不能给拉黑自己的人点赞，也不能给自己拉黑的人点赞）
        if (userId != comment.AuthorId)
        {
            var isBlockedBy = await _userBlockService.IsBlockedByAsync(userId, comment.AuthorId);
            if (isBlockedBy)
                throw new BusinessException(BusinessError.CannotLikeBlockedComment);
            
            var hasBlocked = await _userBlockService.IsBlockedAsync(userId, comment.AuthorId);
            if (hasBlocked)
                throw new BusinessException(BusinessError.CannotLikeHasBlockedComment);
        }
    }
    
    public async Task<bool> AddLikeToCacheAsync(long commentId, string userId)
    {
        var likesSetKey = RedisKeys.CommentLike.GetCommentLikesSetKey(commentId);
        
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var result = await _redis.SetAddAsync(likesSetKey, userId);
                if (result)
                {
                    await _redis.KeyExpireAsync(likesSetKey, TimeSpan.FromHours(_config.LikesSetExpireHours));
                }
                return result;
            },
            fallbackAction: async _ =>
            {
                // Redis 不可用时，检查数据库
                return !await _commentLikeRepository.ExistsAsync(commentId, userId);
            },
            operationKey: $"CommentLike:SetAdd:{commentId}:{userId}");
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Comment/ICommentLikeApplicationService.cs
public interface ICommentLikeApplicationService
{
    Task<bool> LikeCommentAsync(long commentId, string userId);
    Task<bool> UnlikeCommentAsync(long commentId, string userId);
    Task<bool> IsCommentLikedByUserAsync(long commentId, string userId);
    Task<Dictionary<long, bool>> AreCommentsLikedByUserAsync(List<long> commentIds, string userId);
}

// ZhiCoreCore/Application/Comment/CommentLikeApplicationService.cs
public class CommentLikeApplicationService : ICommentLikeApplicationService
{
    private readonly ICommentLikeDomainService _domainService;
    private readonly ICommentCacheService _commentCacheService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<CommentLikeApplicationService> _logger;
    
    public async Task<bool> LikeCommentAsync(long commentId, string userId)
    {
        // 1. 验证点赞操作
        await _domainService.ValidateLikeAsync(commentId, userId);
        
        // 2. 添加点赞状态到缓存（原子操作，防重复）
        var added = await _domainService.AddLikeToCacheAsync(commentId, userId);
        if (!added)
        {
            // 已经点赞过了
            return false;
        }
        
        // 3. 获取评论信息（用于事件）
        var comment = await _commentCacheService.GetCommentBasicInfoAsync(commentId);
        
        // 4. 发布领域事件（异步处理数据库写入和通知）
        await _eventDispatcher.DispatchAsync(new CommentLikedEvent
        {
            CommentId = commentId,
            PostId = comment!.PostId,
            UserId = userId,
            CommentAuthorId = comment.AuthorId,
            IsLike = true
        });
        
        _logger.LogDebug("用户 {UserId} 点赞评论 {CommentId} 成功", userId, commentId);
        
        return true;
    }
    
    public async Task<bool> UnlikeCommentAsync(long commentId, string userId)
    {
        // 1. 验证评论存在
        var comment = await _commentCacheService.GetCommentBasicInfoAsync(commentId);
        if (comment == null)
            throw new BusinessException(BusinessError.CommentNotFound);
        
        // 2. 从缓存移除点赞状态
        var removed = await _domainService.RemoveLikeFromCacheAsync(commentId, userId);
        if (!removed)
        {
            // 还没有点赞
            return false;
        }
        
        // 3. 发布领域事件
        await _eventDispatcher.DispatchAsync(new CommentLikedEvent
        {
            CommentId = commentId,
            PostId = comment.PostId,
            UserId = userId,
            CommentAuthorId = comment.AuthorId,
            IsLike = false
        });
        
        _logger.LogDebug("用户 {UserId} 取消点赞评论 {CommentId} 成功", userId, commentId);
        
        return true;
    }
}
```

## 领域事件

### CommentLikedEvent

```csharp
public record CommentLikedEvent : DomainEventBase
{
    public override string EventType => nameof(CommentLikedEvent);
    
    public long CommentId { get; init; }
    public long PostId { get; init; }
    public string UserId { get; init; } = string.Empty;
    public string CommentAuthorId { get; init; } = string.Empty;
    public bool IsLike { get; init; } // true: 点赞, false: 取消点赞
}
```

### 事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/Comment/CommentLikedEventHandler.cs
public class CommentLikedEventHandler : IDomainEventHandler<CommentLikedEvent>
{
    private readonly ICommentLikeRepository _likeRepository;
    private readonly ICommentStatsService _statsService;
    private readonly INotificationService _notificationService;
    private readonly ILogger<CommentLikedEventHandler> _logger;
    
    public async Task HandleAsync(CommentLikedEvent @event, CancellationToken ct = default)
    {
        // 1. 更新数据库
        if (@event.IsLike)
        {
            var like = new CommentLike
            {
                CommentId = @event.CommentId,
                UserId = @event.UserId,
                CreateTime = DateTimeOffset.UtcNow
            };
            await _likeRepository.AddAsync(like);
            await _statsService.IncrementLikeCountAsync(@event.CommentId);
        }
        else
        {
            await _likeRepository.DeleteAsync(@event.CommentId, @event.UserId);
            await _statsService.DecrementLikeCountAsync(@event.CommentId);
        }
        
        // 2. 发送/清理通知
        if (@event.UserId != @event.CommentAuthorId)
        {
            if (@event.IsLike)
            {
                await _notificationService.NotifyCommentLikedAsync(
                    @event.CommentAuthorId,
                    @event.PostId,
                    "", // postTitle
                    "", // likerName
                    @event.UserId,
                    @event.CommentId,
                    ""); // commentContent
            }
            else
            {
                await _notificationService.RemoveCommentLikeNotificationAsync(
                    @event.CommentAuthorId,
                    @event.CommentId,
                    @event.UserId);
            }
        }
        
        _logger.LogDebug("评论点赞事件处理完成: CommentId={CommentId}, IsLike={IsLike}", 
            @event.CommentId, @event.IsLike);
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `comment:likes:{commentId}` | Set | 评论的点赞用户集合 | 24 小时 |
| `comment:{commentId}:stats` | Hash | 评论统计（含点赞数） | 10 分钟 |

### 缓存一致性

```
点赞操作
    │
    ├─► Redis Set 添加（立即生效）
    │
    └─► MQ 消息 ─► Consumer ─► 数据库更新
                              │
                              └─► 热度队列更新
```

## 降级策略

### Redis 不可用时

```csharp
// 点赞状态检查降级到数据库
var isLiked = await _redisPolicyProvider.ExecuteWithFallbackAsync(
    async _ => await _redis.SetContainsAsync(likesSetKey, userId),
    fallbackAction: async _ =>
    {
        _logger.LogWarning("Redis 不可用，点赞状态检查降级到数据库");
        return await _likeRepository.ExistsAsync(commentId, userId);
    },
    operationKey: $"CommentLike:IsLiked:{commentId}:{userId}");
```

### MQ 不可用时

```csharp
// 降级到直接写数据库（使用事务保证一致性）
await _rabbitMqPolicyProvider.ExecuteWithFallbackAsync(
    async _ => await _eventPublisher.PublishCommentLikeBatchAsync(message),
    fallbackAction: async _ =>
    {
        _logger.LogWarning("RabbitMQ 不可用，点赞降级到直接写数据库");
        
        await using var transaction = await _dbContext.Database.BeginTransactionAsync();
        try
        {
            // 写入点赞记录
            _dbContext.CommentLikes.Add(like);
            
            // 更新统计
            await _dbContext.CommentStats
                .Where(cs => cs.CommentId == commentId)
                .ExecuteUpdateAsync(s => s
                    .SetProperty(cs => cs.LikeCount, cs => cs.LikeCount + 1));
            
            await _dbContext.SaveChangesAsync();
            await transaction.CommitAsync();
        }
        catch
        {
            await transaction.RollbackAsync();
            throw;
        }
    },
    operationKey: $"CommentLike:Publish:{commentId}:{userId}");
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 10 | 6 |
| 跨上下文依赖 | INotificationService (通过 scope) | 无（通过事件解耦） |
| 方法行数 | LikeCommentAsync 150+ 行 | LikeCommentAsync 30 行 |
| 降级逻辑 | 内联在主方法中 | 事件处理器统一处理 |
