# ViewCountService 详细设计

## 服务概述

ViewCountService 负责文章阅读量统计，包括：
- 阅读量递增（去重）
- 阅读量查询
- 批量获取阅读量
- 热门文章阅读量缓存

## 当前实现分析

### 依赖清单（3 依赖）

```csharp
public class PostViewCountService(
    IConnectionMultiplexer redis,
    ILogger<PostViewCountService> logger,
    IServiceScopeFactory serviceScopeFactory
) : IViewCountService
```

### 当前架构特点

1. **Redis 优先**：阅读量存储在 Redis 中
2. **去重机制**：使用 HyperLogLog 或 Set 去重
3. **批量同步**：定期将 Redis 数据同步到数据库

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IViewCountRepository.cs
public interface IViewCountRepository
{
    /// <summary>
    /// 递增阅读量（带去重）
    /// </summary>
    /// <param name="postId">文章ID</param>
    /// <param name="userId">用户ID（可选，用于去重）</param>
    /// <param name="clientIp">客户端IP（用于匿名用户去重）</param>
    /// <returns>是否为新增阅读</returns>
    Task<bool> IncrementAsync(long postId, string? userId, string? clientIp);
    
    /// <summary>
    /// 获取阅读量
    /// </summary>
    Task<long> GetViewCountAsync(long postId);
    
    /// <summary>
    /// 批量获取阅读量
    /// </summary>
    Task<Dictionary<long, long>> GetViewCountsAsync(IEnumerable<long> postIds);
    
    /// <summary>
    /// 设置阅读量（用于数据对账）
    /// </summary>
    Task SetViewCountAsync(long postId, long count);
    
    /// <summary>
    /// 同步到数据库
    /// </summary>
    Task SyncToDatabaseAsync(long postId);
    
    /// <summary>
    /// 批量同步到数据库
    /// </summary>
    Task SyncBatchToDatabaseAsync(IEnumerable<long> postIds);
}
```

### Repository 实现

```csharp
// BlogCore/Infrastructure/Repositories/ViewCountRepository.cs
public class ViewCountRepository : IViewCountRepository
{
    private readonly IDatabase _redis;
    private readonly AppDbContext _dbContext;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<ViewCountRepository> _logger;
    
    // 去重窗口：同一用户/IP 在 1 小时内只计算一次阅读
    private readonly TimeSpan _deduplicationWindow = TimeSpan.FromHours(1);
    
    public async Task<bool> IncrementAsync(long postId, string? userId, string? clientIp)
    {
        // 生成去重 Key
        var deduplicationKey = GenerateDeduplicationKey(postId, userId, clientIp);
        
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                // 1. 检查是否已阅读（去重）
                var alreadyViewed = await _redis.StringGetAsync(deduplicationKey);
                if (alreadyViewed.HasValue)
                {
                    return false; // 已阅读，不计数
                }
                
                // 2. 原子递增阅读量
                var statsHashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
                await _redis.HashIncrementAsync(
                    statsHashKey, 
                    RedisKeys.PostCache.PostStatsFieldViewCount, 
                    1);
                
                // 3. 设置去重标记
                await _redis.StringSetAsync(deduplicationKey, "1", _deduplicationWindow);
                
                // 4. 添加到待同步队列
                await AddToSyncQueueAsync(postId);
                
                return true;
            },
            fallbackAction: _ =>
            {
                // Redis 不可用时，跳过阅读量统计（不影响用户体验）
                _logger.LogWarning("Redis 不可用，跳过阅读量统计: PostId={PostId}", postId);
                return Task.FromResult(false);
            },
            operationKey: $"ViewCount:Increment:{postId}");
    }
    
    public async Task<long> GetViewCountAsync(long postId)
    {
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var statsHashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
                var count = await _redis.HashGetAsync(
                    statsHashKey, 
                    RedisKeys.PostCache.PostStatsFieldViewCount);
                
                if (count.HasValue)
                {
                    return (long)count;
                }
                
                // Redis 未命中，从数据库获取
                var dbCount = await GetViewCountFromDatabaseAsync(postId);
                
                // 回填 Redis
                await _redis.HashSetAsync(
                    statsHashKey, 
                    RedisKeys.PostCache.PostStatsFieldViewCount, 
                    dbCount);
                
                return dbCount;
            },
            fallbackAction: async _ =>
            {
                _logger.LogWarning("Redis 不可用，降级到数据库查询");
                return await GetViewCountFromDatabaseAsync(postId);
            },
            operationKey: $"ViewCount:Get:{postId}");
    }
    
    public async Task<Dictionary<long, long>> GetViewCountsAsync(IEnumerable<long> postIds)
    {
        var postIdList = postIds.ToList();
        
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var result = new Dictionary<long, long>();
                var batch = _redis.CreateBatch();
                var tasks = new Dictionary<long, Task<RedisValue>>();
                
                foreach (var postId in postIdList)
                {
                    var statsHashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
                    tasks[postId] = batch.HashGetAsync(
                        statsHashKey, 
                        RedisKeys.PostCache.PostStatsFieldViewCount);
                }
                
                batch.Execute();
                
                var missingIds = new List<long>();
                foreach (var (postId, task) in tasks)
                {
                    var value = await task;
                    if (value.HasValue)
                    {
                        result[postId] = (long)value;
                    }
                    else
                    {
                        missingIds.Add(postId);
                    }
                }
                
                // 批量从数据库获取缺失的
                if (missingIds.Any())
                {
                    var dbCounts = await GetViewCountsBatchFromDatabaseAsync(missingIds);
                    foreach (var (postId, count) in dbCounts)
                    {
                        result[postId] = count;
                    }
                }
                
                return result;
            },
            fallbackAction: async _ =>
            {
                _logger.LogWarning("Redis 不可用，批量降级到数据库查询");
                return await GetViewCountsBatchFromDatabaseAsync(postIdList);
            },
            operationKey: "ViewCount:GetBatch");
    }
    
    public async Task SyncToDatabaseAsync(long postId)
    {
        var count = await GetViewCountAsync(postId);
        
        await _dbContext.PostStats
            .Where(ps => ps.PostId == postId)
            .ExecuteUpdateAsync(s => s
                .SetProperty(ps => ps.ViewCount, count)
                .SetProperty(ps => ps.UpdateTime, DateTimeOffset.UtcNow));
    }
    
    public async Task SyncBatchToDatabaseAsync(IEnumerable<long> postIds)
    {
        var counts = await GetViewCountsAsync(postIds);
        
        foreach (var (postId, count) in counts)
        {
            await _dbContext.PostStats
                .Where(ps => ps.PostId == postId)
                .ExecuteUpdateAsync(s => s
                    .SetProperty(ps => ps.ViewCount, count)
                    .SetProperty(ps => ps.UpdateTime, DateTimeOffset.UtcNow));
        }
    }
    
    private string GenerateDeduplicationKey(long postId, string? userId, string? clientIp)
    {
        var identifier = !string.IsNullOrEmpty(userId) ? userId : clientIp ?? "anonymous";
        return $"view:dedup:{postId}:{identifier}";
    }
    
    private async Task AddToSyncQueueAsync(long postId)
    {
        // 添加到待同步 Set，由后台任务定期同步
        await _redis.SetAddAsync("view:sync:pending", postId);
    }
    
    private async Task<long> GetViewCountFromDatabaseAsync(long postId)
    {
        var stats = await _dbContext.PostStats
            .Where(ps => ps.PostId == postId)
            .Select(ps => ps.ViewCount)
            .FirstOrDefaultAsync();
        
        return stats;
    }
    
    private async Task<Dictionary<long, long>> GetViewCountsBatchFromDatabaseAsync(IEnumerable<long> postIds)
    {
        return await _dbContext.PostStats
            .Where(ps => postIds.Contains(ps.PostId))
            .ToDictionaryAsync(ps => ps.PostId, ps => ps.ViewCount);
    }
}
```

### Application Service

```csharp
// BlogCore/Application/Post/IPostViewApplicationService.cs
public interface IPostViewApplicationService
{
    Task RecordViewAsync(long postId, string? userId, string? clientIp);
    Task<long> GetViewCountAsync(long postId);
}

// BlogCore/Application/Post/PostViewApplicationService.cs
public class PostViewApplicationService : IPostViewApplicationService
{
    private readonly IPostRepository _postRepository;
    private readonly IViewCountRepository _viewCountRepository;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<PostViewApplicationService> _logger;
    
    public async Task RecordViewAsync(long postId, string? userId, string? clientIp)
    {
        // 1. 验证文章存在且已发布
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null || post.Status != PostStatus.Published)
        {
            return; // 静默失败，不影响用户体验
        }
        
        // 2. 递增阅读量
        var isNewView = await _viewCountRepository.IncrementAsync(postId, userId, clientIp);
        
        // 3. 发布领域事件（用于热度更新）
        if (isNewView)
        {
            await _eventDispatcher.DispatchAsync(new PostViewedEvent
            {
                PostId = postId,
                UserId = userId,
                ClientIp = clientIp
            });
        }
    }
}
```

## 领域事件

### PostViewedEvent

```csharp
public record PostViewedEvent : DomainEventBase
{
    public override string EventType => nameof(PostViewedEvent);
    
    public long PostId { get; init; }
    public string? UserId { get; init; }
    public string? ClientIp { get; init; }
}
```

### 事件处理器

```csharp
public class PostViewedEventHandler : IDomainEventHandler<PostViewedEvent>
{
    private readonly IPostHotnessService _hotnessService;
    private readonly ILogger<PostViewedEventHandler> _logger;
    
    public async Task HandleAsync(PostViewedEvent @event, CancellationToken ct = default)
    {
        try
        {
            // 更新文章热度
            await _hotnessService.UpdatePostHotnessOnViewAsync(@event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新热度失败: PostId={PostId}", @event.PostId);
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `post:stats:{postId}` | Hash | view_count 字段 | 永久 |
| `view:dedup:{postId}:{identifier}` | String | 去重标记 | 1 小时 |
| `view:sync:pending` | Set | 待同步的文章ID | 永久 |

### 同步策略

```csharp
// BlogCore/Services/Background/ViewCountSyncService.cs
public class ViewCountSyncService : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            await SyncPendingViewCountsAsync();
            await Task.Delay(TimeSpan.FromMinutes(5), stoppingToken);
        }
    }
    
    private async Task SyncPendingViewCountsAsync()
    {
        // 1. 获取待同步的文章ID
        var pendingIds = await _redis.SetMembersAsync("view:sync:pending");
        
        if (!pendingIds.Any()) return;
        
        // 2. 批量同步到数据库
        var postIds = pendingIds.Select(v => (long)v).ToList();
        await _viewCountRepository.SyncBatchToDatabaseAsync(postIds);
        
        // 3. 清空待同步队列
        await _redis.KeyDeleteAsync("view:sync:pending");
        
        _logger.LogInformation("同步 {Count} 篇文章的阅读量到数据库", postIds.Count);
    }
}
```

## 降级策略

### Redis 不可用时

阅读量统计是非关键功能，Redis 不可用时：
1. 跳过阅读量递增（不影响用户阅读）
2. 查询时降级到数据库
3. 记录警告日志

```csharp
fallbackAction: _ =>
{
    _logger.LogWarning("Redis 不可用，跳过阅读量统计");
    return Task.FromResult(false);
}
```

## Cached Decorator

```csharp
// BlogCore/Infrastructure/Caching/CachedViewCountService.cs
public class CachedViewCountService : IViewCountService
{
    private readonly IViewCountService _inner;
    private readonly IDatabase _redis;
    private readonly IMemoryCache _memoryCache;
    
    public async Task<long> GetViewCountAsync(long postId)
    {
        // 1. 先查内存缓存（热点数据）
        var cacheKey = $"viewcount:{postId}";
        if (_memoryCache.TryGetValue(cacheKey, out long cachedCount))
        {
            return cachedCount;
        }
        
        // 2. 查 Redis
        var count = await _inner.GetViewCountAsync(postId);
        
        // 3. 写入内存缓存（短 TTL）
        _memoryCache.Set(cacheKey, count, TimeSpan.FromSeconds(30));
        
        return count;
    }
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 3 | 4 |
| 去重逻辑 | 散落在服务中 | 集中在 Repository |
| 同步逻辑 | 手动管理 | 后台服务自动同步 |
| 热度更新 | 直接调用 | 事件驱动 |
