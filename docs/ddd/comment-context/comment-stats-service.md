# CommentStatsService 详细设计

## 服务概述

CommentStatsService 负责评论统计信息的管理，包括：
- 点赞数管理（递增/递减/设置）
- 回复数管理（递增/递减/设置）
- 热度分数管理
- 批量统计查询

## 当前实现分析

### 依赖清单（6 个依赖）

```csharp
public class CommentStatsService(
    AppDbContext dbContext,
    IConnectionMultiplexer redis,
    IServiceScopeFactory serviceScopeFactory,
    ICommentCacheService commentCacheService,
    IRedisPolicyProvider redisPolicyProvider,
    ILogger<CommentStatsService> logger) : ICommentStatsService
```

### 当前架构特点

1. **数据库为准**：统计数据以数据库为最终一致性来源
2. **原子更新**：使用 ExecuteUpdateAsync 进行原子操作
3. **缓存辅助**：Redis Hash 缓存统计数据，加速读取
4. **Polly 保护**：Redis 操作有熔断保护

### 数据模型

```csharp
public class CommentStats
{
    public long CommentId { get; set; }      // 评论 ID（主键）
    public int LikeCount { get; set; }       // 点赞数
    public int ReplyCount { get; set; }      // 回复数
    public decimal? HotScore { get; set; }   // 热度分数
    public DateTimeOffset UpdateTime { get; set; }
}
```

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/ICommentStatsRepository.cs
public interface ICommentStatsRepository
{
    /// <summary>
    /// 获取评论统计
    /// </summary>
    Task<CommentStats?> GetByCommentIdAsync(long commentId);
    
    /// <summary>
    /// 获取或创建评论统计
    /// </summary>
    Task<CommentStats> GetOrCreateAsync(long commentId);
    
    /// <summary>
    /// 原子递增点赞数
    /// </summary>
    Task<int> IncrementLikeCountAsync(long commentId, int delta = 1);
    
    /// <summary>
    /// 原子递增回复数
    /// </summary>
    Task<int> IncrementReplyCountAsync(long commentId, int delta = 1);
    
    /// <summary>
    /// 设置点赞数
    /// </summary>
    Task SetLikeCountAsync(long commentId, int count);
    
    /// <summary>
    /// 设置回复数
    /// </summary>
    Task SetReplyCountAsync(long commentId, int count);
    
    /// <summary>
    /// 批量获取点赞数
    /// </summary>
    Task<Dictionary<long, int>> GetLikeCountsBatchAsync(List<long> commentIds);
    
    /// <summary>
    /// 批量获取回复数
    /// </summary>
    Task<Dictionary<long, int>> GetReplyCountsBatchAsync(List<long> commentIds);
}
```

### Domain Service

```csharp
// BlogCore/Domain/Services/ICommentStatsDomainService.cs
public interface ICommentStatsDomainService
{
    /// <summary>
    /// 更新点赞数（数据库 + 缓存）
    /// </summary>
    Task UpdateLikeCountAsync(long commentId, int delta);
    
    /// <summary>
    /// 更新回复数（数据库 + 缓存）
    /// </summary>
    Task UpdateReplyCountAsync(long commentId, int delta);
    
    /// <summary>
    /// 计算热度分数
    /// </summary>
    decimal CalculateHotScore(int likeCount, int replyCount, DateTimeOffset createTime);
}

// BlogCore/Domain/Services/CommentStatsDomainService.cs
public class CommentStatsDomainService : ICommentStatsDomainService
{
    private readonly ICommentStatsRepository _statsRepository;
    private readonly ICommentCacheService _cacheService;
    private readonly ILogger<CommentStatsDomainService> _logger;
    
    public async Task UpdateLikeCountAsync(long commentId, int delta)
    {
        // 1. 更新数据库（原子操作）
        var newCount = await _statsRepository.IncrementLikeCountAsync(commentId, delta);
        
        // 2. 同步更新缓存（静默执行，失败不影响主流程）
        try
        {
            await _cacheService.UpdateStatsInCacheAsync(commentId, likeCount: newCount);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "更新评论统计缓存失败: CommentId={CommentId}", commentId);
        }
        
        _logger.LogDebug("评论 {CommentId} 点赞数已更新: delta={Delta}, newCount={NewCount}", 
            commentId, delta, newCount);
    }
    
    public decimal CalculateHotScore(int likeCount, int replyCount, DateTimeOffset createTime)
    {
        // 热度公式：点赞数 * 2 + 回复数 * 3 + 时间衰减
        var baseScore = likeCount * 2 + replyCount * 3;
        
        // 时间衰减：每天衰减 5%
        var ageInDays = (DateTimeOffset.UtcNow - createTime).TotalDays;
        var decayFactor = Math.Pow(0.95, ageInDays);
        
        return (decimal)(baseScore * decayFactor);
    }
}
```

### Application Service

```csharp
// BlogCore/Application/Comment/ICommentStatsApplicationService.cs
public interface ICommentStatsApplicationService
{
    Task IncrementLikeCountAsync(long commentId);
    Task DecrementLikeCountAsync(long commentId);
    Task IncrementReplyCountAsync(long commentId);
    Task DecrementReplyCountAsync(long commentId);
    Task<int> GetLikeCountAsync(long commentId);
    Task<int> GetReplyCountAsync(long commentId);
    Task<Dictionary<long, int>> GetLikeCountsBatchAsync(List<long> commentIds);
    Task<Dictionary<long, int>> GetReplyCountsBatchAsync(List<long> commentIds);
}

// BlogCore/Application/Comment/CommentStatsApplicationService.cs
public class CommentStatsApplicationService : ICommentStatsApplicationService
{
    private readonly ICommentStatsDomainService _domainService;
    private readonly ICommentStatsRepository _statsRepository;
    private readonly ICommentCacheService _cacheService;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<CommentStatsApplicationService> _logger;
    
    public async Task<Dictionary<long, int>> GetLikeCountsBatchAsync(List<long> commentIds)
    {
        if (commentIds == null || !commentIds.Any())
            return new Dictionary<long, int>();
        
        // 优先从缓存批量获取
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var result = new Dictionary<long, int>();
                var missingIds = new List<long>();
                
                // 从缓存获取
                var cachedData = await _cacheService.BatchGetCommentsWithStatsAsync(commentIds);
                
                foreach (var commentId in commentIds)
                {
                    if (cachedData.TryGetValue(commentId, out var data))
                    {
                        result[commentId] = data.LikeCount;
                    }
                    else
                    {
                        missingIds.Add(commentId);
                    }
                }
                
                // 缓存未命中的从数据库获取
                if (missingIds.Any())
                {
                    var dbCounts = await _statsRepository.GetLikeCountsBatchAsync(missingIds);
                    foreach (var (id, count) in dbCounts)
                    {
                        result[id] = count;
                    }
                }
                
                return result;
            },
            fallbackAction: async _ =>
            {
                // Redis 不可用，降级到数据库
                _logger.LogWarning("Redis 不可用，批量获取点赞数降级到数据库");
                return await _statsRepository.GetLikeCountsBatchAsync(commentIds);
            },
            operationKey: "CommentStats:GetLikeCountsBatch");
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `comment:{commentId}:stats` | Hash | 评论统计 | 10 分钟 |
| `comment:hot:urgent` | ZSet | 紧急热度更新队列 | 永久 |
| `comment:hot:scheduled` | ZSet | 定时热度更新队列 | 永久 |

### Hash 结构

```
comment:{commentId}:stats
├── like_count: 点赞数
├── reply_count: 回复数
├── hot_score: 热度分数
└── update_time: 更新时间
```

### 缓存更新策略

```csharp
// 写入时更新缓存
public async Task UpdateStatsInCacheAsync(long commentId, int? likeCount = null, int? replyCount = null)
{
    var hashKey = $"comment:{commentId}:stats";
    var entries = new List<HashEntry>();
    
    if (likeCount.HasValue)
        entries.Add(new HashEntry("like_count", likeCount.Value));
    
    if (replyCount.HasValue)
        entries.Add(new HashEntry("reply_count", replyCount.Value));
    
    entries.Add(new HashEntry("update_time", DateTimeOffset.UtcNow.ToUnixTimeSeconds()));
    
    await _redis.HashSetAsync(hashKey, entries.ToArray());
    await _redis.KeyExpireAsync(hashKey, TimeSpan.FromMinutes(10));
}
```

## 降级策略

### Redis 不可用时

```csharp
// 批量查询降级到数据库
return await _redisPolicyProvider.ExecuteWithFallbackAsync(
    async _ =>
    {
        // 从 Redis Hash 批量获取
        var result = new Dictionary<long, int>();
        foreach (var commentId in commentIds)
        {
            var hashKey = $"comment:{commentId}:stats";
            var likeCount = await _redis.HashGetAsync(hashKey, "like_count");
            result[commentId] = likeCount.HasValue ? (int)likeCount : 0;
        }
        return result;
    },
    fallbackAction: async _ =>
    {
        _logger.LogWarning("Redis 不可用，批量获取统计降级到数据库");
        return await _statsRepository.GetLikeCountsBatchAsync(commentIds);
    },
    operationKey: "CommentStats:BatchGet");
```

## 热度更新队列

评论点赞/回复后，需要更新热度分数：

```csharp
// 将评论加入热度更新队列
public async Task EnqueueHotnessUpdateAsync(long commentId, bool urgent = false)
{
    var timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
    
    if (urgent)
    {
        // 紧急队列：立即处理
        await _redis.SortedSetAddAsync(RedisKeys.CommentCache.HotUpdateUrgentQueue, commentId, timestamp);
    }
    
    // 定时队列：批量处理
    await _redis.SortedSetAddAsync(RedisKeys.CommentCache.HotUpdateScheduledQueue, commentId, timestamp);
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 6 | 5 |
| 职责 | 清晰 | 更清晰（分层） |
| 缓存策略 | Hash 缓存 | Hash 缓存 + 批量优化 |
| 可测试性 | 中 | 高（Repository 可 Mock） |
