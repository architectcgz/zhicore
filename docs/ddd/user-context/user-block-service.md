# UserBlockService 详细设计

## 服务概述

UserBlockService 负责用户拉黑功能，包括：
- 拉黑/取消拉黑用户
- 拉黑状态查询（支持两级缓存）
- 拉黑列表管理
- 拉黑对社交功能的影响

## 当前实现分析

### 依赖清单（3 依赖）

```csharp
public class UserBlockService(
    AppDbContext dbContext,
    IMemoryCache memoryCache,
    ILogger<UserBlockService> logger
) : IUserBlockService
```

### 当前架构特点

1. **两级缓存**：Memory Cache + Redis（在装饰器中）
2. **高频查询优化**：拉黑状态查询是高频操作
3. **影响范围广**：拉黑会影响关注、私信、评论等功能

### 问题分析

1. **缺少领域事件**：拉黑后没有通知其他服务
2. **关联操作分散**：拉黑后需要取消关注，逻辑分散

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/IUserBlockRepository.cs
public interface IUserBlockRepository
{
    /// <summary>
    /// 获取拉黑关系
    /// </summary>
    Task<UserBlock?> GetAsync(string blockerId, string blockedUserId);
    
    /// <summary>
    /// 检查是否已拉黑
    /// </summary>
    Task<bool> IsBlockedAsync(string blockerId, string blockedUserId);
    
    /// <summary>
    /// 检查是否被拉黑
    /// </summary>
    Task<bool> IsBlockedByAsync(string userId, string targetUserId);
    
    /// <summary>
    /// 检查双向拉黑状态
    /// </summary>
    Task<(bool IsBlocked, bool IsBlockedBy)> GetBidirectionalBlockStatusAsync(
        string userId, string targetUserId);
    
    /// <summary>
    /// 添加拉黑关系
    /// </summary>
    Task<UserBlock> AddAsync(UserBlock block);
    
    /// <summary>
    /// 删除拉黑关系
    /// </summary>
    Task<bool> DeleteAsync(string blockerId, string blockedUserId);
    
    /// <summary>
    /// 获取拉黑列表
    /// </summary>
    Task<(IReadOnlyList<UserBasicInfo> Items, int Total)> GetBlockListAsync(
        string userId, int page, int pageSize);
    
    /// <summary>
    /// 获取拉黑数量
    /// </summary>
    Task<int> GetBlockCountAsync(string userId);
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/IUserBlockDomainService.cs
public interface IUserBlockDomainService
{
    /// <summary>
    /// 创建拉黑关系
    /// </summary>
    Task<UserBlock> CreateBlockAsync(string blockerId, string blockedUserId);
    
    /// <summary>
    /// 验证拉黑操作
    /// </summary>
    Task ValidateBlockAsync(string blockerId, string blockedUserId);
}

// ZhiCoreCore/Domain/Services/UserBlockDomainService.cs
public class UserBlockDomainService : IUserBlockDomainService
{
    private readonly IUserRepository _userRepository;
    private readonly ISnowflakeIdService _snowflakeIdService;
    
    public async Task<UserBlock> CreateBlockAsync(string blockerId, string blockedUserId)
    {
        await ValidateBlockAsync(blockerId, blockedUserId);
        
        return new UserBlock
        {
            Id = _snowflakeIdService.NextId(),
            BlockerId = blockerId,
            BlockedUserId = blockedUserId,
            BlockTime = DateTimeOffset.UtcNow
        };
    }
    
    public async Task ValidateBlockAsync(string blockerId, string blockedUserId)
    {
        // 1. 不能拉黑自己
        if (blockerId == blockedUserId)
            throw new BusinessException(BusinessError.CannotBlockSelf);
        
        // 2. 检查被拉黑用户是否存在
        var userExists = await _userRepository.ExistsAsync(blockedUserId);
        if (!userExists)
            throw new BusinessException(BusinessError.UserNotFound);
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/User/IUserBlockApplicationService.cs
public interface IUserBlockApplicationService
{
    Task<bool> BlockUserAsync(string blockerId, string blockedUserId);
    Task<bool> UnblockUserAsync(string blockerId, string blockedUserId);
    Task<bool> IsBlockedAsync(string blockerId, string blockedUserId);
    Task<bool> IsBlockedByAsync(string userId, string targetUserId);
    Task<PaginatedResult<UserBasicInfo>> GetBlockListAsync(string userId, int page, int pageSize);
}

// ZhiCoreCore/Application/User/UserBlockApplicationService.cs
public class UserBlockApplicationService : IUserBlockApplicationService
{
    private readonly IUserBlockRepository _userBlockRepository;
    private readonly IUserBlockDomainService _userBlockDomainService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<UserBlockApplicationService> _logger;
    
    public async Task<bool> BlockUserAsync(string blockerId, string blockedUserId)
    {
        // 1. 检查是否已拉黑
        var exists = await _userBlockRepository.IsBlockedAsync(blockerId, blockedUserId);
        if (exists)
        {
            _logger.LogInformation("用户 {BlockerId} 已经拉黑了用户 {BlockedUserId}", blockerId, blockedUserId);
            return true;
        }
        
        // 2. 通过 Domain Service 创建拉黑关系
        var block = await _userBlockDomainService.CreateBlockAsync(blockerId, blockedUserId);
        
        // 3. 持久化
        await _userBlockRepository.AddAsync(block);
        
        // 4. 发布领域事件
        await _eventDispatcher.DispatchAsync(new UserBlockedEvent
        {
            BlockerId = blockerId,
            BlockedUserId = blockedUserId
        });
        
        _logger.LogInformation("用户 {BlockerId} 成功拉黑了用户 {BlockedUserId}", blockerId, blockedUserId);
        return true;
    }
    
    public async Task<bool> UnblockUserAsync(string blockerId, string blockedUserId)
    {
        var deleted = await _userBlockRepository.DeleteAsync(blockerId, blockedUserId);
        
        if (deleted)
        {
            // 发布领域事件
            await _eventDispatcher.DispatchAsync(new UserUnblockedEvent
            {
                BlockerId = blockerId,
                BlockedUserId = blockedUserId
            });
            
            _logger.LogInformation("用户 {BlockerId} 成功取消拉黑用户 {BlockedUserId}", blockerId, blockedUserId);
        }
        
        return true;
    }
}
```

## 领域事件

### UserBlockedEvent

```csharp
public record UserBlockedEvent : DomainEventBase
{
    public override string EventType => nameof(UserBlockedEvent);
    
    public string BlockerId { get; init; } = string.Empty;
    public string BlockedUserId { get; init; } = string.Empty;
}
```

### UserUnblockedEvent

```csharp
public record UserUnblockedEvent : DomainEventBase
{
    public override string EventType => nameof(UserUnblockedEvent);
    
    public string BlockerId { get; init; } = string.Empty;
    public string BlockedUserId { get; init; } = string.Empty;
}
```

### 事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/User/UserBlockedEventHandler.cs
public class UserBlockedEventHandler : IDomainEventHandler<UserBlockedEvent>
{
    private readonly IUserFollowRepository _userFollowRepository;
    private readonly IDatabase _redis;
    private readonly IMemoryCache _memoryCache;
    private readonly ILogger<UserBlockedEventHandler> _logger;
    
    public async Task HandleAsync(UserBlockedEvent @event, CancellationToken ct = default)
    {
        // 1. 取消双向关注关系
        try
        {
            await _userFollowRepository.DeleteAsync(@event.BlockerId, @event.BlockedUserId);
            await _userFollowRepository.DeleteAsync(@event.BlockedUserId, @event.BlockerId);
            _logger.LogDebug("已取消双向关注关系: {BlockerId} <-> {BlockedUserId}", 
                @event.BlockerId, @event.BlockedUserId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "取消关注关系失败");
        }
        
        // 2. 失效缓存
        try
        {
            // 失效 Redis 缓存
            var key1 = $"block:{@event.BlockerId}:{@event.BlockedUserId}";
            var key2 = $"block:{@event.BlockedUserId}:{@event.BlockerId}";
            await _redis.KeyDeleteAsync(new RedisKey[] { key1, key2 });
            
            // 失效内存缓存
            _memoryCache.Remove(key1);
            _memoryCache.Remove(key2);
            
            // 失效消息验证缓存
            var msgKey1 = $"msg:validation:{@event.BlockerId}:{@event.BlockedUserId}";
            var msgKey2 = $"msg:validation:{@event.BlockedUserId}:{@event.BlockerId}";
            await _redis.KeyDeleteAsync(new RedisKey[] { msgKey1, msgKey2 });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "失效拉黑缓存失败");
        }
    }
}
```

## 缓存策略

### 两级缓存架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   L1: Memory Cache                               │
│                   TTL: 5 分钟                                    │
│                   命中率高，延迟低                                │
└─────────────────────────────────────────────────────────────────┘
                              │ Miss
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   L2: Redis Cache                                │
│                   TTL: 1 小时                                    │
│                   跨实例共享                                     │
└─────────────────────────────────────────────────────────────────┘
                              │ Miss
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Database                                    │
└─────────────────────────────────────────────────────────────────┘
```

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `block:{blockerId}:{blockedUserId}` | String | 拉黑状态 (1/0) | 1 小时 |
| `user:{userId}:blocklist` | Set | 用户拉黑列表 | 永久 |

### Cached Decorator

```csharp
// 已在 CachedUserBlockService 中实现
public class CachedUserBlockService : CacheDecoratorBase, IUserBlockService
{
    private readonly IMemoryCache _memoryCache;
    private static readonly TimeSpan MemoryCacheTTL = TimeSpan.FromMinutes(5);
    private static readonly TimeSpan RedisCacheTTL = TimeSpan.FromHours(1);
    
    public async Task<bool> IsBlockedAsync(string blockerId, string blockedUserId)
    {
        var memoryCacheKey = $"block:{blockerId}:{blockedUserId}";
        
        // L1: Memory Cache
        if (_memoryCache.TryGetValue(memoryCacheKey, out bool memoryResult))
        {
            return memoryResult;
        }
        
        // L2: Redis Cache
        var redisResult = await GetBlockStatusFromRedisAsync(blockerId, blockedUserId);
        if (redisResult.HasValue)
        {
            _memoryCache.Set(memoryCacheKey, redisResult.Value, MemoryCacheTTL);
            return redisResult.Value;
        }
        
        // L3: Database
        var dbResult = await _inner.IsBlockedAsync(blockerId, blockedUserId);
        
        // 回填两级缓存
        _memoryCache.Set(memoryCacheKey, dbResult, MemoryCacheTTL);
        await SetBlockStatusToRedisAsync(blockerId, blockedUserId, dbResult);
        
        return dbResult;
    }
}
```

## 拉黑影响范围

| 功能 | 影响 |
|------|------|
| 关注 | 拉黑后自动取消双向关注，无法再次关注 |
| 私信 | 无法发送私信给被拉黑用户 |
| 评论 | 无法在被拉黑用户的文章下评论 |
| 查看 | 可以查看被拉黑用户的公开内容 |
| 通知 | 不再收到被拉黑用户的任何通知 |

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 3 | 5 |
| 领域事件 | 无 | UserBlockedEvent, UserUnblockedEvent |
| 关联操作 | 手动调用 | 事件处理器自动处理 |
| 缓存失效 | 手动 | 事件驱动 |
