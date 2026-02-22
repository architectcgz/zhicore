# PostStatsService 详细设计

## 服务概述

PostStatsService 负责文章统计数据管理，包括：
- 点赞数、评论数、收藏数、阅读量的读取
- 统计数据的原子更新
- 批量获取统计数据
- Redis 与数据库的数据同步

## 当前实现分析

### 依赖清单（4 依赖）

```csharp
public class PostStatsService(
    AppDbContext dbContext,
    IConnectionMultiplexer redis,
    ILogger<PostStatsService> logger,
    IServiceScopeFactory serviceScopeFactory
) : IPostStatsService
```

### 数据存储

- **数据库**：`post_stats` 表，存储持久化统计数据
- **Redis**：`post:stats:{postId}` Hash，存储实时统计数据

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/IPostStatsRepository.cs
public interface IPostStatsRepository
{
    /// <summary>
    /// 获取文章统计
    /// </summary>
    Task<PostStatsDto?> GetStatsAsync(long postId);
    
    /// <summary>
    /// 批量获取文章统计
    /// </summary>
    Task<Dictionary<long, PostStatsDto>> GetStatsBatchAsync(IEnumerable<long> postIds);
    
    /// <summary>
    /// 创建或获取统计记录
    /// </summary>
    Task<PostStats> GetOrCreateAsync(long postId);
    
    /// <summary>
    /// 原子递增点赞数
    /// </summary>
    Task<int> IncrementLikeCountAsync(long postId, int delta = 1);
    
    /// <summary>
    /// 原子递增评论数
    /// </summary>
    Task<int> IncrementCommentCountAsync(long postId, int delta = 1);
    
    /// <summary>
    /// 原子递增收藏数
    /// </summary>
    Task<int> IncrementFavoriteCountAsync(long postId, int delta = 1);
    
    /// <summary>
    /// 原子递增阅读量
    /// </summary>
    Task<long> IncrementViewCountAsync(long postId, long delta = 1);
    
    /// <summary>
    /// 设置统计数据（用于数据对账）
    /// </summary>
    Task SetStatsAsync(long postId, PostStatsDto stats);
    
    /// <summary>
    /// 同步 Redis 到数据库
    /// </summary>
    Task SyncToDatabaseAsync(long postId);
}
```

### Repository 实现

```csharp
// ZhiCoreCore/Infrastructure/Repositories/PostStatsRepository.cs
public class PostStatsRepository : IPostStatsRepository
{
    private readonly AppDbContext _dbContext;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<PostStatsRepository> _logger;
    
    public async Task<PostStatsDto?> GetStatsAsync(long postId)
    {
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var hashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
                var hashEntries = await _redis.HashGetAllAsync(hashKey);
                
                if (hashEntries.Length == 0)
                {
                    // Redis 未命中，从数据库获取并回填
                    var dbStats = await GetStatsFromDatabaseAsync(postId);
                    if (dbStats != null)
                    {
                        await SetStatsToRedisAsync(postId, dbStats);
                    }
                    return dbStats;
                }
                
                return ParseHashEntries(postId, hashEntries);
            },
            fallbackAction: async _ =>
            {
                _logger.LogWarning("Redis 不可用，降级到数据库查询");
                return await GetStatsFromDatabaseAsync(postId);
            },
            operationKey: $"PostStats:Get:{postId}");
    }
    
    public async Task<Dictionary<long, PostStatsDto>> GetStatsBatchAsync(IEnumerable<long> postIds)
    {
        var postIdList = postIds.ToList();
        var result = new Dictionary<long, PostStatsDto>();
        
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var batch = _redis.CreateBatch();
                var tasks = new Dictionary<long, Task<HashEntry[]>>();
                
                foreach (var postId in postIdList)
                {
                    var hashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
                    tasks[postId] = batch.HashGetAllAsync(hashKey);
                }
                
                batch.Execute();
                
                var missingIds = new List<long>();
                foreach (var (postId, task) in tasks)
                {
                    var entries = await task;
                    if (entries.Length > 0)
                    {
                        result[postId] = ParseHashEntries(postId, entries);
                    }
                    else
                    {
                        missingIds.Add(postId);
                    }
                }
                
                // 批量从数据库获取缺失的
                if (missingIds.Any())
                {
                    var dbStats = await GetStatsBatchFromDatabaseAsync(missingIds);
                    foreach (var (postId, stats) in dbStats)
                    {
                        result[postId] = stats;
                        // 异步回填 Redis
                        _ = SetStatsToRedisAsync(postId, stats);
                    }
                }
                
                return result;
            },
            fallbackAction: async _ =>
            {
                _logger.LogWarning("Redis 不可用，批量降级到数据库查询");
                return await GetStatsBatchFromDatabaseAsync(postIdList);
            },
            operationKey: "PostStats:GetBatch");
    }
    
    public async Task<int> IncrementLikeCountAsync(long postId, int delta = 1)
    {
        // 1. 原子更新 Redis
        var hashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
        var newCount = await _redis.HashIncrementAsync(
            hashKey, 
            RedisKeys.PostCache.PostStatsFieldLikeCount, 
            delta);
        
        // 2. 原子更新数据库
        await _dbContext.PostStats
            .Where(ps => ps.PostId == postId)
            .ExecuteUpdateAsync(s => s
                .SetProperty(ps => ps.LikeCount, ps => Math.Max(0, ps.LikeCount + delta))
                .SetProperty(ps => ps.UpdateTime, DateTimeOffset.UtcNow));
        
        return (int)newCount;
    }
    
    public async Task<int> IncrementCommentCountAsync(long postId, int delta = 1)
    {
        var hashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
        var newCount = await _redis.HashIncrementAsync(
            hashKey, 
            RedisKeys.PostCache.PostStatsFieldCommentCount, 
            delta);
        
        await _dbContext.PostStats
            .Where(ps => ps.PostId == postId)
            .ExecuteUpdateAsync(s => s
                .SetProperty(ps => ps.CommentCount, ps => Math.Max(0, ps.CommentCount + delta))
                .SetProperty(ps => ps.UpdateTime, DateTimeOffset.UtcNow));
        
        return (int)newCount;
    }
    
    private async Task<PostStatsDto?> GetStatsFromDatabaseAsync(long postId)
    {
        var stats = await _dbContext.PostStats
            .Where(ps => ps.PostId == postId)
            .Select(ps => new PostStatsDto
            {
                PostId = ps.PostId,
                LikeCount = ps.LikeCount,
                CommentCount = ps.CommentCount,
                FavoriteCount = ps.FavoriteCount,
                ViewCount = ps.ViewCount
            })
            .FirstOrDefaultAsync();
        
        return stats;
    }
    
    private async Task SetStatsToRedisAsync(long postId, PostStatsDto stats)
    {
        var hashKey = RedisKeys.PostCache.GetPostStatsHashKey(postId);
        var entries = new HashEntry[]
        {
            new(RedisKeys.PostCache.PostStatsFieldLikeCount, stats.LikeCount),
            new(RedisKeys.PostCache.PostStatsFieldCommentCount, stats.CommentCount),
            new(RedisKeys.PostCache.PostStatsFieldFavoriteCount, stats.FavoriteCount),
            new(RedisKeys.PostCache.PostStatsFieldViewCount, stats.ViewCount)
        };
        
        await _redis.HashSetAsync(hashKey, entries);
    }
    
    private PostStatsDto ParseHashEntries(long postId, HashEntry[] entries)
    {
        var dict = entries.ToDictionary(e => e.Name.ToString(), e => e.Value);
        
        return new PostStatsDto
        {
            PostId = postId,
            LikeCount = dict.TryGetValue(RedisKeys.PostCache.PostStatsFieldLikeCount, out var like) 
                ? (int)like : 0,
            CommentCount = dict.TryGetValue(RedisKeys.PostCache.PostStatsFieldCommentCount, out var comment) 
                ? (int)comment : 0,
            FavoriteCount = dict.TryGetValue(RedisKeys.PostCache.PostStatsFieldFavoriteCount, out var fav) 
                ? (int)fav : 0,
            ViewCount = dict.TryGetValue(RedisKeys.PostCache.PostStatsFieldViewCount, out var view) 
                ? (long)view : 0
        };
    }
}
```

## 缓存策略

### Redis Hash 结构

```
Key: post:stats:{postId}
Fields:
  - like_count: int
  - comment_count: int
  - favorite_count: int
  - view_count: long
```

### 数据一致性

1. **写操作**：同时更新 Redis 和数据库（原子操作）
2. **读操作**：优先读 Redis，未命中回源数据库并回填
3. **数据对账**：定期任务比对 Redis 和数据库，修复不一致

### 对账服务

```csharp
// ZhiCoreCore/Services/Background/PostStatsReconciliationService.cs
public class PostStatsReconciliationService : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            await ReconcileStatsAsync();
            await Task.Delay(TimeSpan.FromMinutes(30), stoppingToken);
        }
    }
    
    private async Task ReconcileStatsAsync()
    {
        // 1. 获取所有有统计数据的文章ID
        // 2. 比对 Redis 和数据库的值
        // 3. 以数据库为准修复 Redis
        // 4. 记录修复日志
    }
}
```

## 事件订阅

PostStatsRepository 订阅以下事件来更新统计：

| 事件 | 处理逻辑 |
|------|---------|
| PostLikedEvent | IncrementLikeCountAsync(+1/-1) |
| PostFavoritedEvent | IncrementFavoriteCountAsync(+1/-1) |
| CommentCreatedEvent | IncrementCommentCountAsync(+1) |
| CommentDeletedEvent | IncrementCommentCountAsync(-N) |
| PostViewedEvent | IncrementViewCountAsync(+1) |

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 4 | 4 |
| 数据一致性 | 手动管理 | Repository 统一管理 |
| 对账逻辑 | 散落各处 | 集中在 Repository |
