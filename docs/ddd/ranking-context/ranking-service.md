# RankingService 详细设计

## 服务概述

RankingService 负责排行榜数据的查询和管理，包括：
- 热门文章排行榜
- 最佳创作者排行榜
- 热议话题排行榜
- 排行榜统计信息

## API 端点映射

| HTTP 方法 | 路由 | 方法 | 说明 |
|-----------|------|------|------|
| GET | `/api/ranking/posts` | GetHotPostsAsync | 获取热门文章 |
| GET | `/api/ranking/authors` | GetTopAuthorsAsync | 获取最佳创作者 |
| GET | `/api/ranking/topics` | GetHotTopicsAsync | 获取热议话题 |
| GET | `/api/ranking/stats` | GetRankingStatsAsync | 获取排行榜统计 |
| DELETE | `/api/admin/ranking/cache` | ClearRankingCacheAsync | 清除排行榜缓存（管理员） |

## 当前实现分析

### 依赖清单（6 个依赖）

```csharp
public class RankingService(
    AppDbContext dbContext,
    IOptions<CacheConfig> cacheConfig,
    ILogger<RankingService> logger,
    IConnectionMultiplexer redis,
    IRedisPolicyProvider redisPolicyProvider,
    ITieredHotnessService? tieredHotnessService = null) : IRankingService
```

### 当前架构特点

1. **缓存优先**：所有排行榜数据优先从 Redis 缓存获取
2. **分层热度集成**：优先使用分层热度服务获取热门文章
3. **Polly 保护**：Redis 操作有熔断保护
4. **数据库回退**：缓存未命中时从数据库计算


## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IRankingRepository.cs
public interface IRankingRepository
{
    /// <summary>
    /// 获取热门文章 ID 列表
    /// </summary>
    Task<List<long>> GetHotPostIdsAsync(int limit);
    
    /// <summary>
    /// 获取热门文章详情（从数据库）
    /// </summary>
    Task<List<HotPostDto>> GetHotPostsWithDetailsAsync(List<long> postIds);
    
    /// <summary>
    /// 获取最近发布的文章（用于数据库回退）
    /// </summary>
    Task<List<PostWithStatsDto>> GetRecentPostsWithStatsAsync(int limit);
    
    /// <summary>
    /// 获取最佳创作者
    /// </summary>
    Task<List<TopAuthorDto>> GetTopAuthorsAsync(int limit);
    
    /// <summary>
    /// 获取热议话题
    /// </summary>
    Task<List<HotTopicDto>> GetHotTopicsAsync(int limit);
    
    /// <summary>
    /// 获取排行榜统计
    /// </summary>
    Task<RankingStatsDto> GetStatsAsync();
}
```

### Repository 实现

```csharp
// BlogCore/Infrastructure/Repositories/RankingRepository.cs
public class RankingRepository : IRankingRepository
{
    private readonly AppDbContext _dbContext;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<RankingRepository> _logger;
    
    public RankingRepository(
        AppDbContext dbContext,
        IConnectionMultiplexer redis,
        IRedisPolicyProvider redisPolicyProvider,
        ILogger<RankingRepository> logger)
    {
        _dbContext = dbContext;
        _redis = redis.GetDatabase();
        _redisPolicyProvider = redisPolicyProvider;
        _logger = logger;
    }
    
    public async Task<List<long>> GetHotPostIdsAsync(int limit)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var values = await _redis.SortedSetRangeByRankAsync(
                    RedisKeys.Ranking.PostHotnessZSetKey, 
                    start: 0, 
                    stop: limit - 1, 
                    order: Order.Descending);
                return values.Select(v => long.Parse(v!)).ToList();
            },
            defaultValue: new List<long>(),
            operationKey: $"Ranking:GetHotPostIds:{limit}");
    }
    
    public async Task<List<HotPostDto>> GetHotPostsWithDetailsAsync(List<long> postIds)
    {
        if (!postIds.Any()) return new List<HotPostDto>();
        
        var posts = await (
            from p in _dbContext.Posts
            join u in _dbContext.Users on p.OwnerId equals u.Id
            join s in _dbContext.PostStats on p.Id equals s.PostId into statsJoin
            from stats in statsJoin.DefaultIfEmpty()
            where postIds.Contains(p.Id) && !p.Deleted && p.Status == PostStatus.Published
            select new HotPostDto
            {
                Id = p.Id,
                Title = p.Title,
                Excerpt = p.Excerpt,
                AuthorId = p.OwnerId,
                AuthorNickName = u.NickName,
                AuthorAvatarUrl = u.AvatarUrl,
                ViewCount = stats != null ? stats.ViewCount : 0,
                LikeCount = stats != null ? stats.LikeCount : 0,
                CommentCount = stats != null ? stats.CommentCount : 0,
                PublishedAt = p.PublishedAt
            }
        ).ToListAsync();
        
        return posts;
    }
    
    public async Task<List<PostWithStatsDto>> GetRecentPostsWithStatsAsync(int limit)
    {
        return await (
            from p in _dbContext.Posts
            join s in _dbContext.PostStats on p.Id equals s.PostId into statsJoin
            from stats in statsJoin.DefaultIfEmpty()
            where !p.Deleted && p.Status == PostStatus.Published
            orderby p.PublishedAt descending
            select new PostWithStatsDto
            {
                Id = p.Id,
                Title = p.Title,
                OwnerId = p.OwnerId,
                PublishedAt = p.PublishedAt,
                ViewCount = stats != null ? stats.ViewCount : 0,
                LikeCount = stats != null ? stats.LikeCount : 0,
                CommentCount = stats != null ? stats.CommentCount : 0
            }
        ).Take(limit).ToListAsync();
    }
    
    public async Task<List<TopAuthorDto>> GetTopAuthorsAsync(int limit)
    {
        // 从数据库聚合计算
        return await (
            from u in _dbContext.Users
            join p in _dbContext.Posts on u.Id equals p.OwnerId into postsJoin
            from post in postsJoin.DefaultIfEmpty()
            where post == null || (!post.Deleted && post.Status == PostStatus.Published)
            group new { u, post } by new { u.Id, u.NickName, u.AvatarUrl } into g
            let postCount = g.Count(x => x.post != null)
            where postCount > 0
            orderby postCount descending
            select new TopAuthorDto
            {
                Id = g.Key.Id,
                NickName = g.Key.NickName,
                AvatarUrl = g.Key.AvatarUrl,
                PostCount = postCount
            }
        ).Take(limit).ToListAsync();
    }
    
    public async Task<List<HotTopicDto>> GetHotTopicsAsync(int limit)
    {
        return await (
            from t in _dbContext.Topics
            where !t.Deleted
            orderby t.PostCount descending
            select new HotTopicDto
            {
                Id = t.Id,
                Name = t.Name,
                Description = t.Description,
                PostCount = t.PostCount,
                FollowerCount = t.FollowerCount
            }
        ).Take(limit).ToListAsync();
    }
    
    public async Task<RankingStatsDto> GetStatsAsync()
    {
        var totalPosts = await _dbContext.Posts
            .CountAsync(p => !p.Deleted && p.Status == PostStatus.Published);
        var totalAuthors = await _dbContext.Posts
            .Where(p => !p.Deleted && p.Status == PostStatus.Published)
            .Select(p => p.OwnerId)
            .Distinct()
            .CountAsync();
        var totalTopics = await _dbContext.Topics.CountAsync(t => !t.Deleted);
        
        return new RankingStatsDto
        {
            TotalPosts = totalPosts,
            TotalAuthors = totalAuthors,
            TotalTopics = totalTopics,
            LastUpdated = DateTimeOffset.UtcNow
        };
    }
}
```

### Domain Service

```csharp
// BlogCore/Domain/Services/IRankingDomainService.cs
public interface IRankingDomainService
{
    /// <summary>
    /// 计算文章热度分数（用于数据库回退）
    /// </summary>
    long CalculatePostHotScore(int viewCount, int likeCount, int commentCount);
    
    /// <summary>
    /// 计算创作者影响力分数
    /// </summary>
    long CalculateAuthorInfluenceScore(int postCount, int totalLikes, long totalViews, int totalComments);
}

// BlogCore/Domain/Services/RankingDomainService.cs
public class RankingDomainService : IRankingDomainService
{
    public long CalculatePostHotScore(int viewCount, int likeCount, int commentCount)
    {
        // 热度公式：阅读数 + 点赞数×3 + 评论数×5
        return viewCount + (long)likeCount * 3 + (long)commentCount * 5;
    }
    
    public long CalculateAuthorInfluenceScore(int postCount, int totalLikes, long totalViews, int totalComments)
    {
        // 影响力公式：文章数×100 + 点赞数×2 + 浏览数×1 + 评论数×5
        return (long)postCount * 100 + (long)totalLikes * 2 + totalViews + (long)totalComments * 5;
    }
}
```

### Application Service

```csharp
// BlogCore/Application/Ranking/IRankingApplicationService.cs
public interface IRankingApplicationService
{
    Task<List<HotPostDto>> GetHotPostsAsync(int limit);
    Task<List<TopAuthorDto>> GetTopAuthorsAsync(int limit);
    Task<List<HotTopicDto>> GetHotTopicsAsync(int limit);
    Task<RankingStatsDto> GetRankingStatsAsync();
    Task<int> ClearRankingCacheAsync();
}

// BlogCore/Application/Ranking/RankingApplicationService.cs
public class RankingApplicationService : IRankingApplicationService
{
    private readonly IRankingDomainService _domainService;
    private readonly IRankingRepository _rankingRepository;
    private readonly ITieredHotnessService? _tieredHotnessService;
    private readonly IRankingCacheService _cacheService;
    private readonly ILogger<RankingApplicationService> _logger;
    
    public async Task<List<HotPostDto>> GetHotPostsAsync(int limit)
    {
        var cacheKey = RedisKeys.Ranking.GetHotPostsKey(limit);
        
        // 1. 尝试从缓存获取
        var cachedData = await _cacheService.GetAsync<List<HotPostDto>>(cacheKey);
        if (cachedData != null)
        {
            _logger.LogDebug("从缓存获取热门文章排行榜，limit: {Limit}", limit);
            return cachedData;
        }
        
        // 2. 获取热门文章 ID（优先使用分层热度服务）
        var hotPostIds = await GetHotPostIdsAsync(limit);
        
        if (!hotPostIds.Any())
        {
            _logger.LogWarning("没有热度数据，回退到数据库计算");
            return await GetHotPostsFromDatabaseAsync(limit);
        }
        
        // 3. 批量获取文章详情
        var hotPosts = await _rankingRepository.GetHotPostsWithDetailsAsync(hotPostIds);
        
        // 4. 按原始热度排序
        var orderedHotPosts = hotPostIds
            .Select(id => hotPosts.FirstOrDefault(p => p.Id == id))
            .Where(p => p != null)
            .Cast<HotPostDto>()
            .ToList();
        
        // 5. 缓存结果
        await _cacheService.SetAsync(cacheKey, orderedHotPosts, TimeSpan.FromMinutes(30));
        
        _logger.LogDebug("获取热门文章排行榜，limit: {Limit}, count: {Count}", limit, orderedHotPosts.Count);
        return orderedHotPosts;
    }
    
    private async Task<List<long>> GetHotPostIdsAsync(int limit)
    {
        // 优先使用分层热度服务
        if (_tieredHotnessService != null)
        {
            var hotPostIds = await _tieredHotnessService.GetHotPostsAsync(limit);
            if (hotPostIds.Any())
            {
                _logger.LogDebug("从分层热度服务获取热门文章 ID，count: {Count}", hotPostIds.Count);
                return hotPostIds;
            }
        }
        
        // 回退到 Redis ZSet
        return await _rankingRepository.GetHotPostIdsAsync(limit);
    }
    
    private async Task<List<HotPostDto>> GetHotPostsFromDatabaseAsync(int limit)
    {
        // 只查询最近1000篇文章，避免全表扫描
        var recentPosts = await _rankingRepository.GetRecentPostsWithStatsAsync(1000);
        
        // 计算热度并排序
        return recentPosts
            .Select(p => new
            {
                Post = p,
                HotScore = _domainService.CalculatePostHotScore(p.ViewCount, p.LikeCount, p.CommentCount)
            })
            .OrderByDescending(x => x.HotScore)
            .Take(limit)
            .Select(x => x.Post)
            .ToList();
    }
}
```

## DTO 定义

### HotPostDto

```csharp
/// <summary>
/// 热门文章 DTO
/// </summary>
public class HotPostDto
{
    public long Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public string? Excerpt { get; set; }
    public string AuthorId { get; set; } = string.Empty;
    public string? AuthorNickName { get; set; }
    public string? AuthorAvatarUrl { get; set; }
    public int ViewCount { get; set; }
    public int LikeCount { get; set; }
    public int CommentCount { get; set; }
    public DateTimeOffset? PublishedAt { get; set; }
    public double HotScore { get; set; }
}
```

### TopAuthorDto

```csharp
/// <summary>
/// 最佳创作者 DTO
/// </summary>
public class TopAuthorDto
{
    public string Id { get; set; } = string.Empty;
    public string NickName { get; set; } = string.Empty;
    public string? AvatarUrl { get; set; }
    public int PostCount { get; set; }
    public long TotalViews { get; set; }
    public int TotalLikes { get; set; }
    public int FollowersCount { get; set; }
    public double InfluenceScore { get; set; }
}
```

### HotTopicDto

```csharp
/// <summary>
/// 热议话题 DTO
/// </summary>
public class HotTopicDto
{
    public long Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public int PostCount { get; set; }
    public int FollowerCount { get; set; }
}
```

### RankingStatsDto

```csharp
/// <summary>
/// 排行榜统计 DTO
/// </summary>
public class RankingStatsDto
{
    public int TotalPosts { get; set; }
    public int TotalAuthors { get; set; }
    public int TotalTopics { get; set; }
    public DateTimeOffset LastUpdated { get; set; }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `ranking:hot_posts:{limit}` | String | 热门文章缓存 | 30 分钟 |
| `ranking:top_authors:{limit}` | String | 最佳创作者缓存 | 30 分钟 |
| `ranking:hot_topics:{limit}` | String | 热议话题缓存 | 30 分钟 |
| `ranking:stats` | String | 排行榜统计缓存 | 1 小时 |
| `ranking:post:hot` | ZSet | 文章热度排行榜 | 永久 |

### 缓存更新策略

```csharp
// 缓存服务封装
public class RankingCacheService : IRankingCacheService
{
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    
    public async Task<T?> GetAsync<T>(string key) where T : class
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var cachedValue = await _redis.StringGetAsync(key);
                return cachedValue.HasValue 
                    ? JsonSerializer.Deserialize<T>(cachedValue!) 
                    : null;
            },
            defaultValue: null,
            operationKey: $"RankingCache:Get:{key}");
    }
    
    public async Task<bool> SetAsync<T>(string key, T data, TimeSpan expiration)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var serializedData = JsonSerializer.Serialize(data);
                return await _redis.StringSetAsync(key, serializedData, expiration);
            },
            defaultValue: false,
            operationKey: $"RankingCache:Set:{key}");
    }
}
```

## 降级策略

### Redis 不可用时

```csharp
// 热门文章 ID 获取降级
return await _redisPolicyProvider.ExecuteSilentAsync(
    async _ =>
    {
        var values = await _database.SortedSetRangeByRankAsync(
            RedisKeys.Ranking.PostHotnessZSetKey, 
            start: 0, 
            stop: limit - 1, 
            order: Order.Descending);
        return values.Select(v => (long)v).ToList();
    },
    defaultValue: new List<long>(),
    operationKey: $"RankingCache:GetHotPostIds:{limit}");
```

### 数据库回退方案

```csharp
// 当 Redis 没有数据时，从数据库计算
private async Task<List<HotPostDto>> GetHotPostsFromDatabaseAsync(int limit)
{
    // 限制查询范围，只查询最近1000篇文章
    var recentPosts = await _dbContext.Posts
        .Where(p => p.Status == PostStatus.Published && !p.Deleted)
        .OrderByDescending(p => p.PublishedAt)
        .Take(1000)
        .Include(p => p.Stats)
        .Include(p => p.Owner)
        .ToListAsync();
    
    // 在内存中计算热度并排序
    return recentPosts
        .Select(p => new
        {
            Post = p,
            HotScore = _domainService.CalculatePostHotScore(
                p.Stats?.ViewCount ?? 0,
                p.Stats?.LikeCount ?? 0,
                p.Stats?.CommentCount ?? 0)
        })
        .OrderByDescending(x => x.HotScore)
        .Take(limit)
        .Select(x => MapToHotPostDto(x.Post))
        .ToList();
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 6 | 5 |
| 缓存逻辑 | 内联在服务中 | 抽取到 CacheService |
| 热度计算 | 内联 | 抽取到 Domain Service |
| 数据访问 | 直接操作 DbContext/Redis | 通过 Repository 抽象 |
| 可测试性 | 中 | 高 |

## DI 注册

```csharp
// BlogCore/Extensions/RankingServiceExtensions.cs
public static IServiceCollection AddRankingQueryServices(this IServiceCollection services)
{
    // Repository
    services.AddScoped<IRankingRepository, RankingRepository>();
    
    // Domain Service
    services.AddScoped<IRankingDomainService, RankingDomainService>();
    
    // Application Service
    services.AddScoped<IRankingApplicationService, RankingApplicationService>();
    
    // 缓存服务
    services.AddScoped<IRankingCacheService, RankingCacheService>();
    
    return services;
}
```
