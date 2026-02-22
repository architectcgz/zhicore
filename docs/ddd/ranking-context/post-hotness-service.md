# PostHotnessService 详细设计

## 服务概述

PostHotnessService 负责文章热度计算和排行榜管理，包括：
- 热度分数计算
- 热度排行榜维护
- 分层热度管理（Hot/Warm/Cold）
- 热度变化过滤

## API 端点映射

| HTTP 方法 | 路由 | 方法 | 说明 |
|-----------|------|------|------|
| GET | `/api/ranking/posts/hot` | GetHotPostsAsync | 获取热门文章排行榜 |
| GET | `/api/ranking/posts/{postId}/hotness` | GetPostHotnessAsync | 获取单篇文章热度 |
| POST | `/api/admin/ranking/recalculate` | RecalculateAllHotnessAsync | 重算所有热度（管理员） |

## 当前实现分析

### 依赖清单（6 依赖）

```csharp
public class TieredHotnessService(
    IHotTierManager hotTierManager,
    ILazyHotnessCalculator lazyCalculator,
    IHotnessChangeFilter changeFilter,
    IHotnessResultCache resultCache,
    ILogger<TieredHotnessService> logger,
    IOptions<HotnessConfig> config
) : IPostHotnessService
```

### 当前架构特点

1. **分层架构**：Hot/Warm/Cold 三层
2. **懒加载计算**：冷门文章按需计算
3. **变化过滤**：过滤微小变化，减少更新频率
4. **结果缓存**：缓存热度排行结果

### 问题分析

1. **被动更新**：其他服务直接调用更新热度
2. **计算逻辑分散**：热度计算逻辑分散在多个类中

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/IPostHotnessRepository.cs
public interface IPostHotnessRepository
{
    /// <summary>
    /// 获取文章热度分数
    /// </summary>
    Task<double?> GetScoreAsync(long postId);
    
    /// <summary>
    /// 批量获取文章热度分数
    /// </summary>
    Task<Dictionary<long, double>> GetScoresBatchAsync(IEnumerable<long> postIds);
    
    /// <summary>
    /// 设置文章热度分数
    /// </summary>
    Task SetScoreAsync(long postId, double score);
    
    /// <summary>
    /// 增加文章热度分数
    /// </summary>
    Task IncrementScoreAsync(long postId, double delta);
    
    /// <summary>
    /// 获取热门文章 ID 列表（按热度降序）
    /// </summary>
    Task<List<long>> GetTopPostIdsAsync(int count, int offset = 0);
    
    /// <summary>
    /// 获取热门文章 ID 和分数
    /// </summary>
    Task<List<(long PostId, double Score)>> GetTopPostsWithScoresAsync(int count, int offset = 0);
    
    /// <summary>
    /// 获取文章排名
    /// </summary>
    Task<long?> GetRankAsync(long postId);
    
    /// <summary>
    /// 移除文章热度记录
    /// </summary>
    Task RemoveAsync(long postId);
    
    /// <summary>
    /// 获取排行榜总数
    /// </summary>
    Task<long> GetTotalCountAsync();
}
```

### Repository 实现

```csharp
// ZhiCoreCore/Infrastructure/Repositories/PostHotnessRepository.cs
public class PostHotnessRepository : IPostHotnessRepository
{
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<PostHotnessRepository> _logger;
    
    private const string HotnessZSetKey = "ranking:post:hot";
    
    public PostHotnessRepository(
        IConnectionMultiplexer redis,
        IRedisPolicyProvider redisPolicyProvider,
        ILogger<PostHotnessRepository> logger)
    {
        _redis = redis.GetDatabase();
        _redisPolicyProvider = redisPolicyProvider;
        _logger = logger;
    }
    
    public async Task<double?> GetScoreAsync(long postId)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ => await _redis.SortedSetScoreAsync(HotnessZSetKey, postId.ToString()),
            defaultValue: null,
            operationKey: $"PostHotness:GetScore:{postId}");
    }
    
    public async Task<Dictionary<long, double>> GetScoresBatchAsync(IEnumerable<long> postIds)
    {
        var result = new Dictionary<long, double>();
        
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var tasks = postIds.Select(async id =>
                {
                    var score = await _redis.SortedSetScoreAsync(HotnessZSetKey, id.ToString());
                    return (Id: id, Score: score);
                });
                
                var scores = await Task.WhenAll(tasks);
                
                foreach (var (id, score) in scores)
                {
                    if (score.HasValue)
                    {
                        result[id] = score.Value;
                    }
                }
                
                return result;
            },
            defaultValue: result,
            operationKey: "PostHotness:GetScoresBatch");
    }
    
    public async Task SetScoreAsync(long postId, double score)
    {
        await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                await _redis.SortedSetAddAsync(HotnessZSetKey, postId.ToString(), score);
                _logger.LogDebug("设置文章热度: PostId={PostId}, Score={Score}", postId, score);
            },
            operationKey: $"PostHotness:SetScore:{postId}");
    }
    
    public async Task IncrementScoreAsync(long postId, double delta)
    {
        await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                await _redis.SortedSetIncrementAsync(HotnessZSetKey, postId.ToString(), delta);
                _logger.LogDebug("增加文章热度: PostId={PostId}, Delta={Delta}", postId, delta);
            },
            operationKey: $"PostHotness:IncrementScore:{postId}");
    }
    
    public async Task<List<long>> GetTopPostIdsAsync(int count, int offset = 0)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var values = await _redis.SortedSetRangeByRankAsync(
                    HotnessZSetKey, offset, offset + count - 1, Order.Descending);
                return values.Select(v => long.Parse(v!)).ToList();
            },
            defaultValue: new List<long>(),
            operationKey: $"PostHotness:GetTopPostIds:{count}:{offset}");
    }
    
    public async Task<List<(long PostId, double Score)>> GetTopPostsWithScoresAsync(int count, int offset = 0)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var entries = await _redis.SortedSetRangeByRankWithScoresAsync(
                    HotnessZSetKey, offset, offset + count - 1, Order.Descending);
                return entries.Select(e => (long.Parse(e.Element!), e.Score)).ToList();
            },
            defaultValue: new List<(long, double)>(),
            operationKey: $"PostHotness:GetTopPostsWithScores:{count}:{offset}");
    }
    
    public async Task<long?> GetRankAsync(long postId)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ => await _redis.SortedSetRankAsync(HotnessZSetKey, postId.ToString(), Order.Descending),
            defaultValue: null,
            operationKey: $"PostHotness:GetRank:{postId}");
    }
    
    public async Task RemoveAsync(long postId)
    {
        await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                await _redis.SortedSetRemoveAsync(HotnessZSetKey, postId.ToString());
                _logger.LogDebug("移除文章热度: PostId={PostId}", postId);
            },
            operationKey: $"PostHotness:Remove:{postId}");
    }
    
    public async Task<long> GetTotalCountAsync()
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ => await _redis.SortedSetLengthAsync(HotnessZSetKey),
            defaultValue: 0L,
            operationKey: "PostHotness:GetTotalCount");
    }
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/IPostHotnessDomainService.cs
public interface IPostHotnessDomainService
{
    /// <summary>
    /// 计算热度分数
    /// </summary>
    double CalculateHotnessScore(int viewCount, int likeCount, int commentCount, 
        int favoriteCount, DateTimeOffset publishedAt);
    
    /// <summary>
    /// 计算热度增量
    /// </summary>
    double CalculateScoreDelta(HotnessUpdateType updateType, int delta = 1);
    
    /// <summary>
    /// 判断热度层级
    /// </summary>
    HotnessTier DetermineTier(double score);
    
    /// <summary>
    /// 计算时间衰减因子
    /// </summary>
    double CalculateTimeDecayFactor(DateTimeOffset publishedAt);
}

// ZhiCoreCore/Domain/Services/PostHotnessDomainService.cs
public class PostHotnessDomainService : IPostHotnessDomainService
{
    private readonly HotnessConfig _config;
    
    public PostHotnessDomainService(IOptions<HotnessConfig> config)
    {
        _config = config.Value;
    }
    
    public double CalculateHotnessScore(int viewCount, int likeCount, int commentCount, 
        int favoriteCount, DateTimeOffset publishedAt)
    {
        // 基础分数
        var baseScore = 
            viewCount * _config.ViewWeight +
            likeCount * _config.LikeWeight +
            commentCount * _config.CommentWeight +
            favoriteCount * _config.FavoriteWeight;
        
        // 时间衰减
        var decayFactor = CalculateTimeDecayFactor(publishedAt);
        
        return baseScore * decayFactor;
    }
    
    public double CalculateScoreDelta(HotnessUpdateType updateType, int delta = 1)
    {
        return updateType switch
        {
            HotnessUpdateType.View => _config.ViewWeight * delta,
            HotnessUpdateType.Like => _config.LikeWeight * delta,
            HotnessUpdateType.Comment => _config.CommentWeight * delta,
            HotnessUpdateType.Favorite => _config.FavoriteWeight * delta,
            HotnessUpdateType.Share => _config.ShareWeight * delta,
            _ => 0
        };
    }
    
    public HotnessTier DetermineTier(double score)
    {
        return score switch
        {
            >= 1000 => HotnessTier.Hot,    // 热门层
            >= 100 => HotnessTier.Warm,    // 温热层
            _ => HotnessTier.Cold          // 冷门层
        };
    }
    
    public double CalculateTimeDecayFactor(DateTimeOffset publishedAt)
    {
        var ageInDays = (DateTimeOffset.UtcNow - publishedAt).TotalDays;
        // 使用指数衰减，半衰期为配置的天数
        return Math.Pow(0.5, ageInDays / _config.HalfLifeDays);
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Ranking/IPostHotnessApplicationService.cs
public interface IPostHotnessApplicationService
{
    Task<IReadOnlyList<HotPostVo>> GetHotPostsAsync(int page, int pageSize, string? tier = null);
    Task<double> GetPostHotnessAsync(long postId);
    Task InitializePostHotnessAsync(long postId);
    Task UpdatePostHotnessAsync(long postId, HotnessUpdateType updateType, int delta = 1);
    Task RecalculateAllHotnessAsync();
}

// ZhiCoreCore/Application/Ranking/PostHotnessApplicationService.cs
public class PostHotnessApplicationService : IPostHotnessApplicationService
{
    private readonly IPostHotnessRepository _hotnessRepository;
    private readonly IPostRepository _postRepository;
    private readonly IPostStatsRepository _postStatsRepository;
    private readonly IPostHotnessDomainService _domainService;
    private readonly IHotTierManager _hotTierManager;
    private readonly IDatabase _redis;
    private readonly ILogger<PostHotnessApplicationService> _logger;
    
    public PostHotnessApplicationService(
        IPostHotnessRepository hotnessRepository,
        IPostRepository postRepository,
        IPostStatsRepository postStatsRepository,
        IPostHotnessDomainService domainService,
        IHotTierManager hotTierManager,
        IConnectionMultiplexer redis,
        ILogger<PostHotnessApplicationService> logger)
    {
        _hotnessRepository = hotnessRepository;
        _postRepository = postRepository;
        _postStatsRepository = postStatsRepository;
        _domainService = domainService;
        _hotTierManager = hotTierManager;
        _redis = redis.GetDatabase();
        _logger = logger;
    }
    
    public async Task<IReadOnlyList<HotPostVo>> GetHotPostsAsync(int page, int pageSize, string? tier = null)
    {
        var cacheKey = $"ranking:post:hot:{tier ?? "all"}:{page}:{pageSize}";
        
        // 尝试从缓存获取
        var cached = await _redis.StringGetAsync(cacheKey);
        if (cached.HasValue)
        {
            return JsonSerializer.Deserialize<List<HotPostVo>>(cached!) ?? new List<HotPostVo>();
        }
        
        // 从 Repository 获取排行数据
        var offset = (page - 1) * pageSize;
        var entries = await _hotnessRepository.GetTopPostsWithScoresAsync(pageSize, offset);
        
        if (!entries.Any())
        {
            return new List<HotPostVo>();
        }
        
        // 获取文章详情
        var postIds = entries.Select(e => e.PostId).ToList();
        var posts = await _postRepository.GetByIdsAsync(postIds);
        var stats = await _postStatsRepository.GetStatsBatchAsync(postIds);
        
        var result = entries.Select((entry, index) =>
        {
            var post = posts.FirstOrDefault(p => p.Id == entry.PostId);
            var stat = stats.TryGetValue(entry.PostId, out var s) ? s : null;
            
            return new HotPostVo
            {
                Rank = offset + index + 1,
                PostId = entry.PostId,
                Title = post?.Title ?? "未知文章",
                AuthorId = post?.OwnerId ?? "",
                HotnessScore = entry.Score,
                ViewCount = stat?.ViewCount ?? 0,
                LikeCount = stat?.LikeCount ?? 0,
                CommentCount = stat?.CommentCount ?? 0,
                PublishedAt = post?.PublishedAt
            };
        }).ToList();
        
        // 缓存结果
        await _redis.StringSetAsync(cacheKey, 
            JsonSerializer.Serialize(result), 
            TimeSpan.FromMinutes(5));
        
        return result;
    }
    
    public async Task<double> GetPostHotnessAsync(long postId)
    {
        var score = await _hotnessRepository.GetScoreAsync(postId);
        return score ?? 0;
    }
    
    public async Task InitializePostHotnessAsync(long postId)
    {
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null) return;
        
        // 初始热度为 0
        await _hotnessRepository.SetScoreAsync(postId, 0);
        
        // 新文章进入 Warm 层
        await _hotTierManager.AssignTierAsync(postId, HotnessTier.Warm);
        
        _logger.LogDebug("文章热度已初始化: PostId={PostId}", postId);
    }
    
    public async Task UpdatePostHotnessAsync(long postId, HotnessUpdateType updateType, int delta = 1)
    {
        // 计算热度增量
        var scoreDelta = _domainService.CalculateScoreDelta(updateType, delta);
        
        if (scoreDelta == 0) return;
        
        // 更新热度分数
        await _hotnessRepository.IncrementScoreAsync(postId, scoreDelta);
        
        // 检查是否需要调整层级
        var newScore = await _hotnessRepository.GetScoreAsync(postId);
        if (newScore.HasValue)
        {
            var newTier = _domainService.DetermineTier(newScore.Value);
            await _hotTierManager.CheckAndAdjustTierAsync(postId, newScore.Value);
        }
        
        // 失效缓存
        await InvalidateHotPostsCacheAsync();
        
        _logger.LogDebug("文章热度已更新: PostId={PostId}, Type={Type}, Delta={Delta}", 
            postId, updateType, scoreDelta);
    }
    
    public async Task RecalculateAllHotnessAsync()
    {
        _logger.LogInformation("开始重算所有文章热度...");
        
        // 获取所有已发布文章
        var posts = await _postRepository.GetAllPublishedPostsAsync();
        var count = 0;
        
        foreach (var post in posts)
        {
            var stats = await _postStatsRepository.GetStatsAsync(post.Id);
            if (stats == null) continue;
            
            var score = _domainService.CalculateHotnessScore(
                stats.ViewCount, stats.LikeCount, stats.CommentCount, 
                stats.FavoriteCount, post.PublishedAt);
            
            await _hotnessRepository.SetScoreAsync(post.Id, score);
            count++;
        }
        
        // 失效所有缓存
        await InvalidateHotPostsCacheAsync();
        
        _logger.LogInformation("文章热度重算完成，共处理 {Count} 篇文章", count);
    }
    
    private async Task InvalidateHotPostsCacheAsync()
    {
        // 删除所有热门文章缓存
        var server = _redis.Multiplexer.GetServer(_redis.Multiplexer.GetEndPoints().First());
        var keys = server.Keys(pattern: "ranking:post:hot:*").ToArray();
        if (keys.Any())
        {
            await _redis.KeyDeleteAsync(keys);
        }
    }
}
```

## DTO 定义

### HotPostVo

```csharp
/// <summary>
/// 热门文章视图对象
/// </summary>
public class HotPostVo
{
    /// <summary>
    /// 排名
    /// </summary>
    public int Rank { get; set; }
    
    /// <summary>
    /// 文章ID
    /// </summary>
    public long PostId { get; set; }
    
    /// <summary>
    /// 文章标题
    /// </summary>
    public string Title { get; set; } = string.Empty;
    
    /// <summary>
    /// 作者ID
    /// </summary>
    public string AuthorId { get; set; } = string.Empty;
    
    /// <summary>
    /// 作者昵称
    /// </summary>
    public string? AuthorNickName { get; set; }
    
    /// <summary>
    /// 作者头像
    /// </summary>
    public string? AuthorAvatarUrl { get; set; }
    
    /// <summary>
    /// 热度分数
    /// </summary>
    public double HotnessScore { get; set; }
    
    /// <summary>
    /// 阅读数
    /// </summary>
    public int ViewCount { get; set; }
    
    /// <summary>
    /// 点赞数
    /// </summary>
    public int LikeCount { get; set; }
    
    /// <summary>
    /// 评论数
    /// </summary>
    public int CommentCount { get; set; }
    
    /// <summary>
    /// 发布时间
    /// </summary>
    public DateTimeOffset? PublishedAt { get; set; }
}
```

### HotnessUpdateType

```csharp
/// <summary>
/// 热度更新类型
/// </summary>
public enum HotnessUpdateType
{
    /// <summary>
    /// 阅读
    /// </summary>
    View = 1,
    
    /// <summary>
    /// 点赞
    /// </summary>
    Like = 2,
    
    /// <summary>
    /// 评论
    /// </summary>
    Comment = 3,
    
    /// <summary>
    /// 收藏
    /// </summary>
    Favorite = 4,
    
    /// <summary>
    /// 分享
    /// </summary>
    Share = 5
}
```

### HotnessTier

```csharp
/// <summary>
/// 热度层级
/// </summary>
public enum HotnessTier
{
    /// <summary>
    /// 热门层（实时更新）
    /// </summary>
    Hot = 1,
    
    /// <summary>
    /// 温热层（定期更新）
    /// </summary>
    Warm = 2,
    
    /// <summary>
    /// 冷门层（懒加载）
    /// </summary>
    Cold = 3
}
```

## 领域事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/Ranking/PostPublishedHotnessHandler.cs
public class PostPublishedHotnessHandler : IDomainEventHandler<PostPublishedEvent>
{
    private readonly IPostHotnessApplicationService _hotnessService;
    private readonly ILogger<PostPublishedHotnessHandler> _logger;
    
    public async Task HandleAsync(PostPublishedEvent @event, CancellationToken ct = default)
    {
        try
        {
            await _hotnessService.InitializePostHotnessAsync(@event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "初始化文章热度失败: PostId={PostId}", @event.PostId);
        }
    }
}

// ZhiCoreCore/Domain/EventHandlers/Ranking/PostLikedHotnessHandler.cs
public class PostLikedHotnessHandler : IDomainEventHandler<PostLikedEvent>
{
    private readonly IPostHotnessApplicationService _hotnessService;
    private readonly ILogger<PostLikedHotnessHandler> _logger;
    
    public async Task HandleAsync(PostLikedEvent @event, CancellationToken ct = default)
    {
        try
        {
            var delta = @event.IsLike ? 1 : -1;
            await _hotnessService.UpdatePostHotnessAsync(
                @event.PostId, HotnessUpdateType.Like, delta);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新文章热度失败: PostId={PostId}", @event.PostId);
        }
    }
}

// ZhiCoreCore/Domain/EventHandlers/Ranking/PostViewedHotnessHandler.cs
public class PostViewedHotnessHandler : IDomainEventHandler<PostViewedEvent>
{
    private readonly IPostHotnessApplicationService _hotnessService;
    private readonly ILogger<PostViewedHotnessHandler> _logger;
    
    public async Task HandleAsync(PostViewedEvent @event, CancellationToken ct = default)
    {
        try
        {
            await _hotnessService.UpdatePostHotnessAsync(
                @event.PostId, HotnessUpdateType.View, 1);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新文章热度失败: PostId={PostId}", @event.PostId);
        }
    }
}

// ZhiCoreCore/Domain/EventHandlers/Ranking/CommentCreatedHotnessHandler.cs
public class CommentCreatedHotnessHandler : IDomainEventHandler<CommentCreatedEvent>
{
    private readonly IPostHotnessApplicationService _hotnessService;
    private readonly ILogger<CommentCreatedHotnessHandler> _logger;
    
    public async Task HandleAsync(CommentCreatedEvent @event, CancellationToken ct = default)
    {
        try
        {
            await _hotnessService.UpdatePostHotnessAsync(
                @event.PostId, HotnessUpdateType.Comment, 1);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新文章热度失败: PostId={PostId}", @event.PostId);
        }
    }
}
```

## 热度算法

### 基础公式

```
热度 = (点赞数 × 3 + 评论数 × 5 + 收藏数 × 4 + 阅读数 × 0.1) × 时间衰减因子
```

### 时间衰减

```csharp
public class HotnessCalculator : IHotnessCalculator
{
    private readonly HotnessConfig _config;
    
    public double CalculateHotness(PostStats stats, DateTimeOffset publishedAt)
    {
        // 基础分数
        var baseScore = 
            stats.LikeCount * _config.LikeWeight +
            stats.CommentCount * _config.CommentWeight +
            stats.FavoriteCount * _config.FavoriteWeight +
            stats.ViewCount * _config.ViewWeight;
        
        // 时间衰减（半衰期 7 天）
        var ageInDays = (DateTimeOffset.UtcNow - publishedAt).TotalDays;
        var decayFactor = Math.Pow(0.5, ageInDays / _config.HalfLifeDays);
        
        return baseScore * decayFactor;
    }
}
```

### 配置

```json
{
  "HotnessConfig": {
    "LikeWeight": 3.0,
    "CommentWeight": 5.0,
    "FavoriteWeight": 4.0,
    "ViewWeight": 0.1,
    "ShareWeight": 2.0,
    "HalfLifeDays": 7,
    "HotTierThreshold": 1000,
    "WarmTierThreshold": 100
  }
}
```

## 分层管理

### 层级定义

| 层级 | 阈值 | 更新频率 | 说明 |
|------|------|---------|------|
| Hot | >= 1000 | 实时 | 热门文章，实时更新 |
| Warm | >= 100 | 5 分钟 | 温热文章，定期更新 |
| Cold | < 100 | 按需 | 冷门文章，懒加载 |

### 层级调整

```csharp
public class HotTierManager : IHotTierManager
{
    public async Task CheckAndAdjustTierAsync(long postId, double score)
    {
        var currentTier = await GetCurrentTierAsync(postId);
        var newTier = DetermineTier(score);
        
        if (currentTier != newTier)
        {
            await AssignTierAsync(postId, newTier);
            _logger.LogDebug("文章层级已调整: PostId={PostId}, {OldTier} -> {NewTier}",
                postId, currentTier, newTier);
        }
    }
    
    private HotnessTier DetermineTier(double score)
    {
        return score switch
        {
            >= 1000 => HotnessTier.Hot,
            >= 100 => HotnessTier.Warm,
            _ => HotnessTier.Cold
        };
    }
}
```

## Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `ranking:post:hot` | ZSet | 文章热度排行榜 | 永久 |
| `ranking:post:hot:{page}:{size}` | String | 排行榜缓存 | 5 分钟 |
| `hotness:tier:{tier}` | Set | 各层级文章ID | 永久 |
| `hotness:post:{postId}` | String | 文章热度缓存 | 5 分钟 |

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 6 | 7 |
| 更新方式 | 被动调用 | 事件驱动 |
| 计算逻辑 | 分散 | 集中在 Domain Service |
| 数据访问 | 直接操作 Redis | 通过 Repository 抽象 |
| 缓存策略 | 部分实现 | 完整实现 |
| 可测试性 | 低 | 高（算法可独立测试） |

## DI 注册

```csharp
// ZhiCoreCore/Extensions/RankingServiceExtensions.cs
public static class RankingServiceExtensions
{
    public static IServiceCollection AddRankingServices(this IServiceCollection services)
    {
        // Repository
        services.AddScoped<IPostHotnessRepository, PostHotnessRepository>();
        
        // Domain Service
        services.AddScoped<IPostHotnessDomainService, PostHotnessDomainService>();
        
        // Application Service
        services.AddScoped<IPostHotnessApplicationService, PostHotnessApplicationService>();
        
        // 事件处理器
        services.AddScoped<IDomainEventHandler<PostPublishedEvent>, PostPublishedHotnessHandler>();
        services.AddScoped<IDomainEventHandler<PostLikedEvent>, PostLikedHotnessHandler>();
        services.AddScoped<IDomainEventHandler<PostViewedEvent>, PostViewedHotnessHandler>();
        services.AddScoped<IDomainEventHandler<CommentCreatedEvent>, CommentCreatedHotnessHandler>();
        
        // 层级管理
        services.AddScoped<IHotTierManager, HotTierManager>();
        
        return services;
    }
}
```
