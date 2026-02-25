# UserFollowService 详细设计

## 服务概述

UserFollowService 负责用户关注功能，包括：
- 关注/取消关注
- 关注状态查询
- 关注列表、粉丝列表
- 相互关注查询
- 关注统计

## 当前实现分析

### 依赖清单（8 依赖）

```csharp
public class UserFollowService(
    AppDbContext dbContext,
    IAntiSpamService antiSpamService,
    IUserBlockService userBlockService,
    ILogger<UserFollowService> logger,
    IEventPublisher eventPublisher,
    ISnowflakeIdService snowflakeIdService,
    IRabbitMqPolicyProvider rabbitMqPolicyProvider,
    INotificationService notificationService  // 跨上下文依赖
) : IUserFollowService
```

### 当前架构特点

1. **数据库优先**：关注关系直接写入数据库
2. **MQ 异步通知**：通过 RabbitMQ 发送关注通知
3. **Polly 降级**：MQ 不可用时降级到直接调用 NotificationService

### 问题分析

1. **跨上下文直接依赖**：`INotificationService` 直接调用
2. **降级逻辑复杂**：MQ 降级逻辑与主逻辑混在一起

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/IUserFollowRepository.cs
public interface IUserFollowRepository
{
    /// <summary>
    /// 获取关注关系
    /// </summary>
    Task<UserFollow?> GetAsync(string followerId, string followingId);
    
    /// <summary>
    /// 检查是否已关注
    /// </summary>
    Task<bool> ExistsAsync(string followerId, string followingId);
    
    /// <summary>
    /// 批量检查关注状态
    /// </summary>
    Task<Dictionary<string, bool>> ExistsBatchAsync(string followerId, IEnumerable<string> followingIds);
    
    /// <summary>
    /// 添加关注关系
    /// </summary>
    Task<UserFollow> AddAsync(UserFollow follow);
    
    /// <summary>
    /// 删除关注关系
    /// </summary>
    Task<bool> DeleteAsync(string followerId, string followingId);
    
    /// <summary>
    /// 获取关注列表
    /// </summary>
    Task<(IReadOnlyList<UserBasicInfo> Items, int Total)> GetFollowingListAsync(
        string userId, int page, int pageSize);
    
    /// <summary>
    /// 获取粉丝列表
    /// </summary>
    Task<(IReadOnlyList<UserBasicInfo> Items, int Total)> GetFollowersListAsync(
        string userId, int page, int pageSize);
    
    /// <summary>
    /// 获取相互关注列表
    /// </summary>
    Task<(IReadOnlyList<UserBasicInfo> Items, int Total)> GetMutualFollowsAsync(
        string userId, int page, int pageSize);
    
    /// <summary>
    /// 获取关注统计
    /// </summary>
    Task<UserFollowStatsDto> GetStatsAsync(string userId);
    
    /// <summary>
    /// 批量获取关注统计
    /// </summary>
    Task<Dictionary<string, UserFollowStatsDto>> GetStatsBatchAsync(IEnumerable<string> userIds);
    
    /// <summary>
    /// 更新关注统计
    /// </summary>
    Task UpdateStatsAsync(string userId);
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/IUserFollowDomainService.cs
public interface IUserFollowDomainService
{
    /// <summary>
    /// 创建关注关系实体
    /// </summary>
    Task<UserFollow> CreateFollowAsync(string followerId, string followingId, bool enableNotification = true);
    
    /// <summary>
    /// 验证关注操作
    /// </summary>
    Task ValidateFollowAsync(string followerId, string followingId);
}

// ZhiCoreCore/Domain/Services/UserFollowDomainService.cs
public class UserFollowDomainService : IUserFollowDomainService
{
    private readonly ISnowflakeIdService _snowflakeIdService;
    private readonly IUserRepository _userRepository;
    private readonly IUserBlockService _userBlockService;
    
    public async Task<UserFollow> CreateFollowAsync(string followerId, string followingId, bool enableNotification = true)
    {
        // 验证关注操作
        await ValidateFollowAsync(followerId, followingId);
        
        return new UserFollow
        {
            Id = _snowflakeIdService.NextId(),
            FollowerId = followerId,
            FollowingId = followingId,
            FollowTime = DateTimeOffset.UtcNow,
            NotificationEnabled = enableNotification
        };
    }
    
    public async Task ValidateFollowAsync(string followerId, string followingId)
    {
        // 1. 不能关注自己
        if (followerId == followingId)
            throw new BusinessException(BusinessError.CannotFollowSelf);
        
        // 2. 检查被关注用户是否存在
        var userExists = await _userRepository.ExistsAsync(followingId);
        if (!userExists)
            throw new BusinessException(BusinessError.UserNotFound);
        
        // 3. 检查拉黑状态
        var isBlockedBy = await _userBlockService.IsBlockedByAsync(followerId, followingId);
        if (isBlockedBy)
            throw new BusinessException(BusinessError.CannotFollowBlockedUser);
        
        var hasBlocked = await _userBlockService.IsBlockedAsync(followerId, followingId);
        if (hasBlocked)
            throw new BusinessException(BusinessError.CannotFollowHasBlocked);
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/User/IUserFollowApplicationService.cs
public interface IUserFollowApplicationService
{
    Task<bool> FollowUserAsync(string followerId, string followingId, bool enableNotification = true);
    Task<bool> UnfollowUserAsync(string followerId, string followingId);
    Task<bool> IsFollowingAsync(string followerId, string followingId);
    Task<Dictionary<string, bool>> GetFollowingStatusBatchAsync(string followerId, IEnumerable<string> followingIds);
    Task<PaginatedResult<UserBasicInfo>> GetFollowingListAsync(string userId, int page, int pageSize);
    Task<PaginatedResult<UserBasicInfo>> GetFollowersListAsync(string userId, int page, int pageSize);
    Task<UserFollowStatsDto> GetUserFollowStatsAsync(string userId);
}

// ZhiCoreCore/Application/User/UserFollowApplicationService.cs
public class UserFollowApplicationService : IUserFollowApplicationService
{
    private readonly IUserRepository _userRepository;
    private readonly IUserFollowRepository _userFollowRepository;
    private readonly IUserFollowDomainService _userFollowDomainService;
    private readonly IAntiSpamService _antiSpamService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<UserFollowApplicationService> _logger;
    
    public async Task<bool> FollowUserAsync(string followerId, string followingId, bool enableNotification = true)
    {
        // 1. 防刷检测
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Follow, followerId, followingId);
        if (antiSpamResult.IsBlocked)
            throw BusinessException.CreateAntiSpamException(BusinessError.AntiSpamLimit, antiSpamResult);
        
        // 2. 检查是否已关注
        var exists = await _userFollowRepository.ExistsAsync(followerId, followingId);
        if (exists)
        {
            _logger.LogInformation("用户 {FollowerId} 已经关注了用户 {FollowingId}", followerId, followingId);
            return true;
        }
        
        // 3. 通过 Domain Service 创建关注关系
        var follow = await _userFollowDomainService.CreateFollowAsync(followerId, followingId, enableNotification);
        
        // 4. 持久化
        await _userFollowRepository.AddAsync(follow);
        
        // 5. 记录操作
        await _antiSpamService.RecordActionAsync(AntiSpamActionType.Follow, followerId, followingId);
        
        // 6. 获取关注者信息
        var follower = await _userRepository.GetByIdAsync(followerId);
        
        // 7. 发布领域事件
        await _eventDispatcher.DispatchAsync(new UserFollowedEvent
        {
            FollowerId = followerId,
            FollowerNickName = follower?.NickName ?? "用户",
            FollowingId = followingId
        });
        
        _logger.LogInformation("用户 {FollowerId} 成功关注了用户 {FollowingId}", followerId, followingId);
        return true;
    }
    
    public async Task<bool> UnfollowUserAsync(string followerId, string followingId)
    {
        // 1. 删除关注关系
        var deleted = await _userFollowRepository.DeleteAsync(followerId, followingId);
        if (!deleted)
        {
            _logger.LogInformation("用户 {FollowerId} 没有关注用户 {FollowingId}", followerId, followingId);
            return true;
        }
        
        // 2. 记录操作
        await _antiSpamService.RecordActionAsync(
            AntiSpamActionType.Follow, followerId, followingId, action: UserAction.Delete);
        
        // 3. 发布领域事件
        await _eventDispatcher.DispatchAsync(new UserUnfollowedEvent
        {
            FollowerId = followerId,
            FollowingId = followingId
        });
        
        _logger.LogInformation("用户 {FollowerId} 成功取消关注用户 {FollowingId}", followerId, followingId);
        return true;
    }
    
    public async Task<PaginatedResult<UserBasicInfo>> GetFollowingListAsync(string userId, int page, int pageSize)
    {
        var (items, total) = await _userFollowRepository.GetFollowingListAsync(userId, page, pageSize);
        
        return new PaginatedResult<UserBasicInfo>
        {
            Items = items.ToList(),
            Total = total,
            Page = page,
            PageSize = pageSize,
            TotalPages = (int)Math.Ceiling((double)total / pageSize)
        };
    }
}
```

## 领域事件

### UserFollowedEvent

```csharp
public record UserFollowedEvent : DomainEventBase
{
    public override string EventType => nameof(UserFollowedEvent);
    
    public string FollowerId { get; init; } = string.Empty;
    public string FollowerNickName { get; init; } = string.Empty;
    public string FollowingId { get; init; } = string.Empty;
}
```

### UserUnfollowedEvent

```csharp
public record UserUnfollowedEvent : DomainEventBase
{
    public override string EventType => nameof(UserUnfollowedEvent);
    
    public string FollowerId { get; init; } = string.Empty;
    public string FollowingId { get; init; } = string.Empty;
}
```

### 事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/User/UserFollowedEventHandler.cs
public class UserFollowedEventHandler : IDomainEventHandler<UserFollowedEvent>
{
    private readonly INotificationService _notificationService;
    private readonly ICreatorHotnessService _creatorHotnessService;
    private readonly ILogger<UserFollowedEventHandler> _logger;
    
    public async Task HandleAsync(UserFollowedEvent @event, CancellationToken ct = default)
    {
        // 1. 发送关注通知
        try
        {
            await _notificationService.NotifyUserFollowedAsync(
                @event.FollowingId,
                @event.FollowerNickName,
                @event.FollowerId);
            
            _logger.LogDebug("已向用户 {FollowingId} 发送关注通知", @event.FollowingId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "发送关注通知失败: FollowingId={FollowingId}", @event.FollowingId);
        }
        
        // 2. 更新创作者热度
        try
        {
            await _creatorHotnessService.UpdateCreatorHotnessOnFollowAsync(@event.FollowingId, true);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新创作者热度失败: FollowingId={FollowingId}", @event.FollowingId);
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `user:following:{userId}` | Set | 用户关注的人 | 永久 |
| `user:followers:{userId}` | Set | 用户的粉丝 | 永久 |
| `user:follow:stats:{userId}` | Hash | 关注统计 | 永久 |

### Cached Decorator

```csharp
// ZhiCoreCore/Infrastructure/Caching/CachedUserFollowService.cs
public class CachedUserFollowService : IUserFollowService
{
    private readonly IUserFollowService _inner;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    
    public async Task<bool> IsFollowingAsync(string followerId, string followingId)
    {
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var key = $"user:following:{followerId}";
                var exists = await _redis.SetContainsAsync(key, followingId);
                
                if (exists) return true;
                
                // Redis 未命中，回源数据库
                var dbResult = await _inner.IsFollowingAsync(followerId, followingId);
                
                // 回填 Redis
                if (dbResult)
                {
                    await _redis.SetAddAsync(key, followingId);
                }
                
                return dbResult;
            },
            fallbackAction: async _ =>
            {
                return await _inner.IsFollowingAsync(followerId, followingId);
            },
            operationKey: $"UserFollow:IsFollowing:{followerId}:{followingId}");
    }
    
    public async Task<bool> FollowUserAsync(string followerId, string followingId, bool enableNotification = true)
    {
        var result = await _inner.FollowUserAsync(followerId, followingId, enableNotification);
        
        if (result)
        {
            // 更新缓存
            await _redisPolicyProvider.ExecuteSilentAsync(async _ =>
            {
                await _redis.SetAddAsync($"user:following:{followerId}", followingId);
                await _redis.SetAddAsync($"user:followers:{followingId}", followerId);
                await _redis.HashIncrementAsync($"user:follow:stats:{followerId}", "following_count", 1);
                await _redis.HashIncrementAsync($"user:follow:stats:{followingId}", "follower_count", 1);
            }, operationKey: $"UserFollow:CacheUpdate:{followerId}:{followingId}");
        }
        
        return result;
    }
}
```

## 降级策略

### Redis 降级

```csharp
public async Task<bool> IsFollowingAsync(string followerId, string followingId)
{
    return await _redisPolicyProvider.ExecuteWithFallbackAsync(
        async _ =>
        {
            // 主逻辑：Redis 查询
            return await _redis.SetContainsAsync(key, followingId);
        },
        fallbackAction: async _ =>
        {
            // 降级：数据库查询
            _logger.LogWarning("Redis 不可用，降级到数据库查询");
            return await _dbContext.UserFollows
                .AnyAsync(f => f.FollowerId == followerId && f.FollowingId == followingId);
        },
        operationKey: $"UserFollow:IsFollowing:{followerId}:{followingId}");
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 8 | 6 |
| 跨上下文依赖 | INotificationService | 无（通过事件解耦） |
| 业务规则 | 散落在服务中 | 集中在 Domain Service |
| 通知发送 | 直接调用 | 事件处理器异步发送 |
