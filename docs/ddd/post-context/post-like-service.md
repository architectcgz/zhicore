# PostLikeService 详细设计

## 服务概述

PostLikeService 负责文章点赞功能，包括：
- 点赞/取消点赞
- 点赞状态查询
- 批量获取点赞数
- Redis + 数据库双写一致性

## 当前实现分析

### 依赖清单（11 依赖）

```csharp
public class PostLikeService(
    AppDbContext dbContext,                    // 数据访问
    IConnectionMultiplexer redis,              // Redis 缓存
    ILogger<PostLikeService> logger,           // 日志
    IServiceScopeFactory serviceScopeFactory,  // 作用域工厂
    IAntiSpamService antiSpamService,          // 防刷服务
    IUserBlockService userBlockService,        // 拉黑服务
    IEventPublisher eventPublisher,            // 事件发布
    IMemoryCache memoryCache,                  // 内存缓存
    IRedisPolicyProvider redisPolicyProvider,  // Redis 弹性策略
    IRabbitMqPolicyProvider rabbitMqPolicyProvider, // MQ 弹性策略
    INotificationService notificationService   // 通知服务（跨上下文）
) : IPostLikeService
```

### 当前架构特点

1. **Redis 优先**：点赞状态存储在 Redis Set 中
2. **Lua 脚本原子操作**：点赞/取消点赞使用 Lua 脚本保证原子性
3. **MQ 异步写库**：通过 RabbitMQ 批量写入数据库
4. **Polly 降级**：MQ 不可用时降级到直接写数据库

### 问题分析

1. **跨上下文直接依赖**：`INotificationService` 直接调用
2. **降级逻辑复杂**：MQ 降级逻辑与主逻辑混在一起
3. **缓存逻辑散落**：部分在服务内部，部分在 Lua 脚本

## DDD 重构设计

### 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    PostLikeController                            │
│                    (Presentation Layer)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                PostLikeApplicationService                        │
│                    (Application Layer)                           │
│                                                                  │
│  依赖：                                                          │
│  - IPostRepository                                               │
│  - IPostLikeRepository                                           │
│  - IAntiSpamService                                              │
│  - IUserBlockService                                             │
│  - IDomainEventDispatcher                                        │
│  - ILogger                                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PostLikeRepository                            │
│                   (Infrastructure Layer)                         │
│                                                                  │
│  依赖：                                                          │
│  - AppDbContext                                                  │
│  - IConnectionMultiplexer (Redis)                                │
│  - IRedisPolicyProvider                                          │
└─────────────────────────────────────────────────────────────────┘
```

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IPostLikeRepository.cs
public interface IPostLikeRepository
{
    /// <summary>
    /// 添加点赞（原子操作：Redis Set + Hash）
    /// </summary>
    /// <returns>true=成功点赞，false=已点赞</returns>
    Task<bool> AddLikeAsync(long postId, string userId);
    
    /// <summary>
    /// 取消点赞（原子操作：Redis Set + Hash）
    /// </summary>
    /// <returns>true=成功取消，false=未点赞</returns>
    Task<bool> RemoveLikeAsync(long postId, string userId);
    
    /// <summary>
    /// 检查是否已点赞
    /// </summary>
    Task<bool> IsLikedAsync(long postId, string userId);
    
    /// <summary>
    /// 批量检查点赞状态
    /// </summary>
    Task<Dictionary<long, bool>> IsLikedBatchAsync(IEnumerable<long> postIds, string userId);
    
    /// <summary>
    /// 获取点赞数
    /// </summary>
    Task<int> GetLikeCountAsync(long postId);
    
    /// <summary>
    /// 批量获取点赞数
    /// </summary>
    Task<Dictionary<long, int>> GetLikeCountsBatchAsync(IEnumerable<long> postIds);
    
    /// <summary>
    /// 设置点赞数（用于数据对账）
    /// </summary>
    Task SetLikeCountAsync(long postId, int count);
}
```

### Repository 实现

```csharp
// BlogCore/Infrastructure/Repositories/PostLikeRepository.cs
public class PostLikeRepository : IPostLikeRepository
{
    private readonly AppDbContext _dbContext;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<PostLikeRepository> _logger;
    
    public PostLikeRepository(
        AppDbContext dbContext,
        IConnectionMultiplexer redis,
        IRedisPolicyProvider redisPolicyProvider,
        ILogger<PostLikeRepository> logger)
    {
        _dbContext = dbContext;
        _redis = redis.GetDatabase();
        _redisPolicyProvider = redisPolicyProvider;
        _logger = logger;
    }
    
    public async Task<bool> AddLikeAsync(long postId, string userId)
    {
        var likesSetKey = RedisKeys.PostLike.GetPostLikesSetKey(postId);
        var statsHashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
        
        // 使用 Lua 脚本原子化操作
        var luaResult = await _redis.ScriptEvaluateAsync(
            RedisLuaScripts.AtomicLikeScript,
            new RedisKey[] { likesSetKey, statsHashKey },
            new RedisValue[] { userId, RedisKeys.PostCache.PostStatsFieldLikeCount }
        );
        
        var resultValue = (long)luaResult;
        if (resultValue == 0)
        {
            return false; // 已点赞
        }
        
        // 异步写入数据库（通过 MQ 或直接写入）
        await PersistLikeAsync(postId, userId, isUnlike: false);
        
        return true;
    }
    
    public async Task<bool> RemoveLikeAsync(long postId, string userId)
    {
        var likesSetKey = RedisKeys.PostLike.GetPostLikesSetKey(postId);
        var statsHashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
        
        var luaResult = await _redis.ScriptEvaluateAsync(
            RedisLuaScripts.AtomicUnlikeScript,
            new RedisKey[] { likesSetKey, statsHashKey },
            new RedisValue[] { userId, RedisKeys.PostCache.PostStatsFieldLikeCount }
        );
        
        var newCount = (long)luaResult;
        if (newCount == -1)
        {
            return false; // 未点赞
        }
        
        await PersistLikeAsync(postId, userId, isUnlike: true);
        
        return true;
    }
    
    public async Task<bool> IsLikedAsync(long postId, string userId)
    {
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var likesSetKey = RedisKeys.PostLike.GetPostLikesSetKey(postId);
                var exists = await _redis.SetContainsAsync(likesSetKey, userId);
                
                if (exists) return true;
                
                // Redis 未命中，回源数据库
                var dbResult = await _dbContext.PostLikes
                    .AnyAsync(pl => pl.PostId == postId && pl.UserId == userId);
                
                // 回填 Redis
                if (dbResult)
                {
                    await _redis.SetAddAsync(likesSetKey, userId);
                }
                
                return dbResult;
            },
            fallbackAction: async _ =>
            {
                _logger.LogWarning("Redis 不可用，降级到数据库查询");
                return await _dbContext.PostLikes
                    .AnyAsync(pl => pl.PostId == postId && pl.UserId == userId);
            },
            operationKey: $"PostLike:IsLiked:{postId}:{userId}");
    }
    
    private async Task PersistLikeAsync(long postId, string userId, bool isUnlike)
    {
        // 这里可以通过 MQ 异步写入，或直接写入数据库
        // 具体实现取决于配置
        if (isUnlike)
        {
            await _dbContext.PostLikes
                .Where(pl => pl.PostId == postId && pl.UserId == userId)
                .ExecuteDeleteAsync();
        }
        else
        {
            var like = new PostLike
            {
                PostId = postId,
                UserId = userId,
                CreateTime = DateTimeOffset.UtcNow
            };
            _dbContext.PostLikes.Add(like);
            await _dbContext.SaveChangesAsync();
        }
    }
}
```

### Application Service

```csharp
// BlogCore/Application/Post/IPostLikeApplicationService.cs
public interface IPostLikeApplicationService
{
    Task LikePostAsync(long postId, string userId);
    Task UnlikePostAsync(long postId, string userId);
    Task<bool> IsPostLikedByUserAsync(long postId, string userId);
    Task<Dictionary<long, int>> GetLikeCountsBatchAsync(IEnumerable<long> postIds);
}

// BlogCore/Application/Post/PostLikeApplicationService.cs
public class PostLikeApplicationService : IPostLikeApplicationService
{
    private readonly IPostRepository _postRepository;
    private readonly IPostLikeRepository _postLikeRepository;
    private readonly IAntiSpamService _antiSpamService;
    private readonly IUserBlockService _userBlockService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<PostLikeApplicationService> _logger;
    
    public async Task LikePostAsync(long postId, string userId)
    {
        // 1. 防刷检测
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Like, userId, postId.ToString());
        if (antiSpamResult.IsBlocked)
            throw BusinessException.CreateAntiSpamException(BusinessError.AntiSpamLimit, antiSpamResult);
        
        // 2. 获取文章信息
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null || post.Status != PostStatus.Published)
            throw new BusinessException(BusinessError.PostNotFound);
        
        // 3. 检查拉黑状态
        if (userId != post.OwnerId)
        {
            var isBlocked = await _userBlockService.IsBlockedByAsync(userId, post.OwnerId);
            if (isBlocked)
                throw new BusinessException(BusinessError.CannotLikeBlockedPost);
        }
        
        // 4. 执行点赞
        var liked = await _postLikeRepository.AddLikeAsync(postId, userId);
        if (!liked)
            throw new BusinessException(BusinessError.PostAlreadyLiked);
        
        // 5. 记录操作
        await _antiSpamService.RecordActionAsync(AntiSpamActionType.Like, userId, postId.ToString());
        
        // 6. 发布领域事件（通知由事件处理器发送）
        await _eventDispatcher.DispatchAsync(new PostLikedEvent
        {
            PostId = postId,
            UserId = userId,
            AuthorId = post.OwnerId,
            PostTitle = post.Title,
            IsLike = true
        });
        
        _logger.LogDebug("用户 {UserId} 点赞文章 {PostId} 成功", userId, postId);
    }
    
    public async Task UnlikePostAsync(long postId, string userId)
    {
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null)
            throw new BusinessException(BusinessError.PostNotFound);
        
        var unliked = await _postLikeRepository.RemoveLikeAsync(postId, userId);
        if (!unliked)
            throw new BusinessException(BusinessError.PostNotLiked);
        
        await _antiSpamService.RecordActionAsync(
            AntiSpamActionType.Like, userId, postId.ToString(), action: UserAction.Delete);
        
        await _eventDispatcher.DispatchAsync(new PostLikedEvent
        {
            PostId = postId,
            UserId = userId,
            AuthorId = post.OwnerId,
            PostTitle = post.Title,
            IsLike = false
        });
        
        _logger.LogDebug("用户 {UserId} 取消点赞文章 {PostId} 成功", userId, postId);
    }
}
```

## 领域事件

### PostLikedEvent

```csharp
public record PostLikedEvent : DomainEventBase
{
    public override string EventType => nameof(PostLikedEvent);
    
    public long PostId { get; init; }
    public string UserId { get; init; } = string.Empty;
    public string AuthorId { get; init; } = string.Empty;
    public string PostTitle { get; init; } = string.Empty;
    public bool IsLike { get; init; } // true=点赞, false=取消点赞
}
```

### 事件处理器

```csharp
// BlogCore/Domain/EventHandlers/Post/PostLikedEventHandler.cs
public class PostLikedEventHandler : IDomainEventHandler<PostLikedEvent>
{
    private readonly INotificationService _notificationService;
    private readonly IPostHotnessService _hotnessService;
    private readonly ILogger<PostLikedEventHandler> _logger;
    
    public async Task HandleAsync(PostLikedEvent @event, CancellationToken ct = default)
    {
        // 1. 发送点赞通知（不通知自己）
        if (@event.IsLike && @event.UserId != @event.AuthorId)
        {
            try
            {
                await _notificationService.NotifyPostLikedAsync(
                    @event.AuthorId,
                    @event.PostId,
                    @event.PostTitle,
                    "用户", // 可以从 UserRepository 获取昵称
                    @event.UserId);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "发送点赞通知失败: PostId={PostId}", @event.PostId);
            }
        }
        
        // 2. 更新热度分数
        try
        {
            await _hotnessService.UpdatePostHotnessOnLikeAsync(@event.PostId, @event.IsLike);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新热度分数失败: PostId={PostId}", @event.PostId);
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `post:likes:{postId}` | Set | 存储点赞用户ID | 永久 |
| `post:stats:{postId}` | Hash | 存储点赞数等统计 | 永久 |
| `post:like:ratelimit:{userId}` | String | 限流计数器 | 1 秒 |

### Lua 脚本（原子点赞）

```lua
-- AtomicLikeScript
-- KEYS[1] = likesSetKey, KEYS[2] = statsHashKey
-- ARGV[1] = userId, ARGV[2] = likeCountField

local added = redis.call('SADD', KEYS[1], ARGV[1])
if added == 0 then
    return 0  -- 已点赞
end

local newCount = redis.call('HINCRBY', KEYS[2], ARGV[2], 1)
return newCount
```

## 降级策略

### Redis 降级

```csharp
public async Task<bool> IsLikedAsync(long postId, string userId)
{
    return await _redisPolicyProvider.ExecuteWithFallbackAsync(
        async _ =>
        {
            // 主逻辑：Redis 查询
            return await _redis.SetContainsAsync(likesSetKey, userId);
        },
        fallbackAction: async _ =>
        {
            // 降级：数据库查询
            _logger.LogWarning("Redis 不可用，降级到数据库查询");
            return await _dbContext.PostLikes
                .AnyAsync(pl => pl.PostId == postId && pl.UserId == userId);
        },
        operationKey: $"PostLike:IsLiked:{postId}:{userId}");
}
```

### MQ 降级

当 RabbitMQ 不可用时，直接写入数据库：

```csharp
await _rabbitMqPolicyProvider.ExecuteWithFallbackAsync(
    async _ =>
    {
        // 主逻辑：发送到 MQ
        await _eventPublisher.PublishPostLikeBatchAsync(likeMessage);
    },
    fallbackAction: async _ =>
    {
        // 降级：直接写数据库
        _logger.LogWarning("RabbitMQ 不可用，降级到直接写数据库");
        await DirectWriteToDatabaseAsync(postId, userId);
    },
    operationKey: $"PostLike:Publish:{postId}:{userId}");
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 11 | 6 |
| 跨上下文依赖 | INotificationService | 无（通过事件解耦） |
| 降级逻辑 | 混在主逻辑中 | 集中在 Repository |
| 通知发送 | 直接调用 | 事件处理器异步发送 |
