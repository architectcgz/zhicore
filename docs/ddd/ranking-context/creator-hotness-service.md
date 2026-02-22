# CreatorHotnessService 详细设计

## 服务概述

CreatorHotnessService 负责创作者热度的计算和管理，包括：
- 创作者热度计算（综合文章热度、粉丝数、活跃度等）
- 热度排行榜维护
- 热度实时更新（关注、发文等事件触发）
- 批量热度重算

## API 端点映射

| HTTP 方法 | 路由 | 方法 | 说明 |
|-----------|------|------|------|
| GET | `/api/ranking/creators/hot` | GetHotCreatorsAsync | 获取热门创作者排行榜 |
| GET | `/api/ranking/creators/{userId}/rank` | GetCreatorHotnessRankAsync | 获取创作者排名 |
| POST | `/api/admin/ranking/creators/recalculate` | RecalculateAllCreatorHotnessAsync | 重算所有创作者热度（管理员） |

## 当前实现分析

### 依赖清单（10 个依赖）

```csharp
public class CreatorHotnessService(
    AppDbContext dbContext,
    IConnectionMultiplexer redis,
    IPostHotnessService postHotnessService,
    IUserService userService,
    IUserFollowService followService,
    IServiceScopeFactory serviceScopeFactory,
    IOptions<CreatorHotnessConfig> hotnessConfig,
    ILogger<CreatorHotnessService> logger,
    IRedisPolicyProvider redisPolicyProvider,
    ITieredHotnessService? tieredHotnessService = null) : ICreatorHotnessService
```

### 热度算法

```
创作者热度 = (
    文章热度总分 × 文章权重 +
    粉丝影响力分数 × 粉丝权重 +
    活跃度分数 × 活跃度权重 +
    质量分数 × 质量权重 +
    最近互动分数 × 互动权重
) × 最近发文奖励
```

### 各分数计算方式

| 分数类型 | 计算方式 |
|---------|---------|
| 文章热度总分 | 最近90天所有文章热度之和 |
| 粉丝影响力 | log10(粉丝数 + 1) × 100 |
| 活跃度 | (文章数 / 天数) × 1000 |
| 质量分数 | 平均文章热度 |
| 最近互动 | 评论数×3 + 评论点赞×1 + 文章点赞×2 |
| 最近发文奖励 | 7天内有发文则乘以奖励系数 |


### 当前架构特点

1. **Redis ZSet 存储排行榜**：按热度分数排序
2. **异步更新**：关注/发文等事件触发后台异步更新
3. **Polly 保护**：Redis 操作有熔断保护
4. **分层热度集成**：支持新的分层热度架构

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/ICreatorHotnessRepository.cs
public interface ICreatorHotnessRepository
{
    /// <summary>
    /// 获取创作者热度分数
    /// </summary>
    Task<double?> GetScoreAsync(string userId);
    
    /// <summary>
    /// 批量获取创作者热度分数
    /// </summary>
    Task<Dictionary<string, double>> GetScoresBatchAsync(IEnumerable<string> userIds);
    
    /// <summary>
    /// 更新创作者热度分数
    /// </summary>
    Task UpdateScoreAsync(string userId, double score);
    
    /// <summary>
    /// 移除创作者热度记录
    /// </summary>
    Task RemoveAsync(string userId);
    
    /// <summary>
    /// 获取热门创作者 ID 列表
    /// </summary>
    Task<List<string>> GetTopCreatorIdsAsync(int count, int offset = 0);
    
    /// <summary>
    /// 获取热门创作者 ID 和分数
    /// </summary>
    Task<List<(string UserId, double Score)>> GetTopCreatorsWithScoresAsync(int count, int offset = 0);
    
    /// <summary>
    /// 获取创作者排名
    /// </summary>
    Task<long?> GetRankAsync(string userId);
    
    /// <summary>
    /// 获取排行榜总数
    /// </summary>
    Task<long> GetTotalCountAsync();
}
```

### Repository 实现

```csharp
// ZhiCoreCore/Infrastructure/Repositories/CreatorHotnessRepository.cs
public class CreatorHotnessRepository : ICreatorHotnessRepository
{
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<CreatorHotnessRepository> _logger;
    
    private const string HotCreatorsZSetKey = "ranking:creator:hot";
    
    public CreatorHotnessRepository(
        IConnectionMultiplexer redis,
        IRedisPolicyProvider redisPolicyProvider,
        ILogger<CreatorHotnessRepository> logger)
    {
        _redis = redis.GetDatabase();
        _redisPolicyProvider = redisPolicyProvider;
        _logger = logger;
    }
    
    public async Task<double?> GetScoreAsync(string userId)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ => await _redis.SortedSetScoreAsync(HotCreatorsZSetKey, userId),
            defaultValue: null,
            operationKey: $"CreatorHotness:GetScore:{userId}");
    }
    
    public async Task<Dictionary<string, double>> GetScoresBatchAsync(IEnumerable<string> userIds)
    {
        var result = new Dictionary<string, double>();
        
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var tasks = userIds.Select(async id =>
                {
                    var score = await _redis.SortedSetScoreAsync(HotCreatorsZSetKey, id);
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
            operationKey: "CreatorHotness:GetScoresBatch");
    }
    
    public async Task UpdateScoreAsync(string userId, double score)
    {
        await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                await _redis.SortedSetAddAsync(HotCreatorsZSetKey, userId, score);
                _logger.LogDebug("更新创作者热度: UserId={UserId}, Score={Score}", userId, score);
            },
            operationKey: $"CreatorHotness:UpdateScore:{userId}");
    }
    
    public async Task RemoveAsync(string userId)
    {
        await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                await _redis.SortedSetRemoveAsync(HotCreatorsZSetKey, userId);
                _logger.LogDebug("移除创作者热度: UserId={UserId}", userId);
            },
            operationKey: $"CreatorHotness:Remove:{userId}");
    }
    
    public async Task<List<string>> GetTopCreatorIdsAsync(int count, int offset = 0)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var values = await _redis.SortedSetRangeByRankAsync(
                    HotCreatorsZSetKey, offset, offset + count - 1, Order.Descending);
                return values.Select(v => v.ToString()).ToList();
            },
            defaultValue: new List<string>(),
            operationKey: $"CreatorHotness:GetTopCreatorIds:{count}:{offset}");
    }
    
    public async Task<List<(string UserId, double Score)>> GetTopCreatorsWithScoresAsync(int count, int offset = 0)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var entries = await _redis.SortedSetRangeByRankWithScoresAsync(
                    HotCreatorsZSetKey, offset, offset + count - 1, Order.Descending);
                return entries.Select(e => (e.Element.ToString(), e.Score)).ToList();
            },
            defaultValue: new List<(string, double)>(),
            operationKey: $"CreatorHotness:GetTopCreatorsWithScores:{count}:{offset}");
    }
    
    public async Task<long?> GetRankAsync(string userId)
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ => await _redis.SortedSetRankAsync(HotCreatorsZSetKey, userId, Order.Descending),
            defaultValue: null,
            operationKey: $"CreatorHotness:GetRank:{userId}");
    }
    
    public async Task<long> GetTotalCountAsync()
    {
        return await _redisPolicyProvider.ExecuteSilentAsync(
            async _ => await _redis.SortedSetLengthAsync(HotCreatorsZSetKey),
            defaultValue: 0L,
            operationKey: "CreatorHotness:GetTotalCount");
    }
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/ICreatorHotnessDomainService.cs
public interface ICreatorHotnessDomainService
{
    /// <summary>
    /// 计算创作者热度分数
    /// </summary>
    Task<double> CalculateHotnessAsync(string userId);
    
    /// <summary>
    /// 计算粉丝影响力分数
    /// </summary>
    double CalculateFollowerInfluence(int followerCount);
    
    /// <summary>
    /// 计算活跃度分数
    /// </summary>
    double CalculateActivityScore(int postCount, double daysSinceFirstPost);
    
    /// <summary>
    /// 计算最近互动分数
    /// </summary>
    Task<double> CalculateRecentInteractionScoreAsync(string userId, DateTimeOffset since);
}

// ZhiCoreCore/Domain/Services/CreatorHotnessDomainService.cs
public class CreatorHotnessDomainService : ICreatorHotnessDomainService
{
    private readonly AppDbContext _dbContext;
    private readonly IPostHotnessService _postHotnessService;
    private readonly IUserFollowService _followService;
    private readonly CreatorHotnessConfig _config;
    
    public async Task<double> CalculateHotnessAsync(string userId)
    {
        // 1. 获取最近90天的已发布文章
        var recentThreshold = DateTimeOffset.UtcNow.AddDays(-90);
        var posts = await GetCreatorPostsAsync(userId, recentThreshold);
        
        if (!posts.Any())
            return 0.0;
        
        // 2. 计算文章热度总分
        var totalPostHotness = posts.Sum(p => 
            _postHotnessService.CalculateHotnessScore(
                p.ViewCount, p.LikeCount, p.CommentCount, p.PublishedAt));
        
        // 3. 计算粉丝影响力
        var followStats = await _followService.GetUserFollowStatsAsync(userId);
        var followerInfluence = CalculateFollowerInfluence(followStats.FollowersCount);
        
        // 4. 计算活跃度
        var daysSinceFirstPost = Math.Max(1, (DateTimeOffset.UtcNow - posts.Min(p => p.PublishedAt)).TotalDays);
        var activityScore = CalculateActivityScore(posts.Count, daysSinceFirstPost);
        
        // 5. 计算质量分数
        var qualityScore = totalPostHotness / posts.Count;
        
        // 6. 计算最近互动分数
        var recentInteractionScore = await CalculateRecentInteractionScoreAsync(
            userId, DateTimeOffset.UtcNow.AddDays(-7));
        
        // 7. 最近发文奖励
        var recentPostsCount = posts.Count(p => p.PublishedAt >= DateTimeOffset.UtcNow.AddDays(-7));
        var recentBonus = recentPostsCount > 0 ? _config.RecentPostsBonus : 1.0;
        
        // 8. 综合计算
        var finalScore = (
            totalPostHotness * _config.PostHotnessWeight +
            followerInfluence * _config.FollowerWeight +
            activityScore * _config.ActivityWeight +
            qualityScore * _config.QualityWeight +
            recentInteractionScore * _config.RecentInteractionWeight
        ) * recentBonus;
        
        return Math.Max(_config.MinCreatorHotnessScore, finalScore);
    }
    
    public double CalculateFollowerInfluence(int followerCount)
    {
        // 使用对数缩放避免差距过大
        return followerCount > 0 ? Math.Log10(followerCount + 1) * 100 : 0;
    }
    
    public double CalculateActivityScore(int postCount, double daysSinceFirstPost)
    {
        // 日均发文数 × 1000
        return (postCount / daysSinceFirstPost) * 1000;
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Ranking/ICreatorHotnessApplicationService.cs
public interface ICreatorHotnessApplicationService
{
    Task<double> CalculateCreatorHotnessAsync(string userId);
    Task<bool> UpdateCreatorHotnessAsync(string userId, double? score = null);
    Task<int> BatchUpdateCreatorHotnessAsync(IEnumerable<string> userIds);
    Task<List<HotCreatorDto>> GetHotCreatorsAsync(int count, int offset = 0, string? currentUserId = null);
    Task<long> GetCreatorHotnessRankAsync(string userId);
    Task<int> RecalculateAllCreatorHotnessAsync(int limit = 500);
}

// ZhiCoreCore/Application/Ranking/CreatorHotnessApplicationService.cs
public class CreatorHotnessApplicationService : ICreatorHotnessApplicationService
{
    private readonly ICreatorHotnessDomainService _domainService;
    private readonly ICreatorHotnessRepository _hotnessRepository;
    private readonly IUserService _userService;
    private readonly IUserFollowService _followService;
    private readonly ILogger<CreatorHotnessApplicationService> _logger;
    
    public async Task<bool> UpdateCreatorHotnessAsync(string userId, double? score = null)
    {
        // 计算或使用提供的分数
        var hotnessScore = score ?? await _domainService.CalculateHotnessAsync(userId);
        
        // 分数过低则移除
        if (hotnessScore < _config.MinCreatorHotnessScore)
        {
            await _hotnessRepository.RemoveAsync(userId);
            _logger.LogDebug("移除创作者 {UserId} 的热度记录（分数过低: {Score}）", userId, hotnessScore);
            return true;
        }
        
        // 更新热度
        await _hotnessRepository.UpdateScoreAsync(userId, hotnessScore);
        _logger.LogDebug("更新创作者 {UserId} 热度分数: {Score}", userId, hotnessScore);
        
        return true;
    }
    
    public async Task<List<HotCreatorDto>> GetHotCreatorsAsync(int count, int offset = 0, string? currentUserId = null)
    {
        // 1. 获取热门创作者 ID
        var creatorIds = await _hotnessRepository.GetTopCreatorIdsAsync(count, offset);
        
        if (!creatorIds.Any())
        {
            _logger.LogWarning("Redis 中没有热门创作者数据，尝试从数据库获取");
            return await GetFallbackHotCreatorsAsync(count, currentUserId);
        }
        
        // 2. 批量获取用户信息
        var userInfoDict = await _userService.GetUserBasicInfoBatchAsync(creatorIds);
        
        // 3. 批量获取统计信息
        var creatorStats = await GetCreatorStatsBatchAsync(creatorIds);
        var followStats = await _followService.GetUserFollowStatsBatchAsync(creatorIds);
        
        // 4. 获取关注状态
        Dictionary<string, bool>? followingStatus = null;
        if (!string.IsNullOrEmpty(currentUserId))
        {
            followingStatus = await _followService.GetFollowingStatusBatchAsync(currentUserId, creatorIds);
        }
        
        // 5. 构建结果
        return creatorIds
            .Where(id => userInfoDict.ContainsKey(id))
            .Select(id => new HotCreatorDto
            {
                Id = id,
                UserName = userInfoDict[id].UserName,
                NickName = userInfoDict[id].NickName,
                AvatarUrl = userInfoDict[id].AvatarUrl,
                PostCount = creatorStats.GetValueOrDefault(id)?.PostCount ?? 0,
                TotalViews = creatorStats.GetValueOrDefault(id)?.TotalViews ?? 0,
                FollowersCount = followStats.GetValueOrDefault(id)?.FollowersCount ?? 0,
                IsFollowed = followingStatus?.GetValueOrDefault(id, false) ?? false
            })
            .ToList();
    }
}
```

## DTO 定义

### HotCreatorDto

```csharp
/// <summary>
/// 热门创作者 DTO
/// </summary>
public class HotCreatorDto
{
    /// <summary>
    /// 用户ID
    /// </summary>
    public string Id { get; set; } = string.Empty;
    
    /// <summary>
    /// 用户名
    /// </summary>
    public string UserName { get; set; } = string.Empty;
    
    /// <summary>
    /// 昵称
    /// </summary>
    public string NickName { get; set; } = string.Empty;
    
    /// <summary>
    /// 头像URL
    /// </summary>
    public string? AvatarUrl { get; set; }
    
    /// <summary>
    /// 文章数
    /// </summary>
    public int PostCount { get; set; }
    
    /// <summary>
    /// 总阅读量
    /// </summary>
    public long TotalViews { get; set; }
    
    /// <summary>
    /// 粉丝数
    /// </summary>
    public int FollowersCount { get; set; }
    
    /// <summary>
    /// 热度分数
    /// </summary>
    public double HotnessScore { get; set; }
    
    /// <summary>
    /// 排名
    /// </summary>
    public int Rank { get; set; }
    
    /// <summary>
    /// 当前用户是否已关注
    /// </summary>
    public bool IsFollowed { get; set; }
}
```

### CreatorStatsDto

```csharp
/// <summary>
/// 创作者统计 DTO
/// </summary>
public class CreatorStatsDto
{
    /// <summary>
    /// 用户ID
    /// </summary>
    public string UserId { get; set; } = string.Empty;
    
    /// <summary>
    /// 文章数
    /// </summary>
    public int PostCount { get; set; }
    
    /// <summary>
    /// 总阅读量
    /// </summary>
    public long TotalViews { get; set; }
    
    /// <summary>
    /// 总点赞数
    /// </summary>
    public int TotalLikes { get; set; }
    
    /// <summary>
    /// 总评论数
    /// </summary>
    public int TotalComments { get; set; }
}
```

## 领域事件订阅

| 事件 | 来源上下文 | 处理逻辑 |
|------|-----------|---------|
| UserFollowedEvent | User Context | 更新被关注者热度 |
| UserUnfollowedEvent | User Context | 更新被取消关注者热度 |
| PostPublishedEvent | Post Context | 更新作者热度 |

### 事件处理器

```csharp
public class UserFollowedEventHandler : IDomainEventHandler<UserFollowedEvent>
{
    private readonly ICreatorHotnessService _hotnessService;
    private readonly ITieredHotnessService? _tieredHotnessService;
    
    public async Task HandleAsync(UserFollowedEvent @event, CancellationToken ct = default)
    {
        // 更新被关注者的热度
        await _hotnessService.UpdateCreatorHotnessAsync(@event.FollowingId);
        
        // 同时触发分层热度更新
        if (_tieredHotnessService != null)
        {
            await _tieredHotnessService.OnCreatorStatsChangedAsync(@event.FollowingId);
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `ranking:creator:hot` | ZSet | 创作者热度排行榜 | 永久 |
| `hotness:creator:{userId}` | String | 创作者热度缓存 | 5 分钟 |

## 降级策略

### Redis 不可用时

```csharp
// 获取热门创作者降级到数据库
return await _redisPolicyProvider.ExecuteWithFallbackAsync(
    async _ =>
    {
        var creatorIds = await _database.SortedSetRangeByRankAsync(
            HotCreatorsZSetKey, offset, offset + count - 1, Order.Descending);
        return creatorIds.Select(id => id.ToString()).ToList();
    },
    fallbackAction: _ =>
    {
        _logger.LogWarning("Redis 不可用，GetHotCreatorIdsAsync 返回空列表");
        return Task.FromResult(new List<string>());
    },
    operationKey: "CreatorHotness:GetHotCreatorIds");
```

## 配置示例

```json
{
  "CreatorHotness": {
    "PostHotnessWeight": 0.3,
    "FollowerWeight": 0.25,
    "ActivityWeight": 0.15,
    "QualityWeight": 0.15,
    "RecentInteractionWeight": 0.15,
    "RecentPostsBonus": 1.2,
    "MinCreatorHotnessScore": 10.0
  }
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 10 | 7 |
| 热度计算 | 内联在服务中 | 抽取到 Domain Service |
| 数据访问 | 直接操作 Redis | 通过 Repository 抽象 |
| 事件处理 | 内部异步 Task.Run | 领域事件处理器 |
| 可测试性 | 低 | 高（算法可独立测试） |

## DI 注册

```csharp
// ZhiCoreCore/Extensions/RankingServiceExtensions.cs
public static IServiceCollection AddCreatorHotnessServices(this IServiceCollection services)
{
    // Repository
    services.AddScoped<ICreatorHotnessRepository, CreatorHotnessRepository>();
    
    // Domain Service
    services.AddScoped<ICreatorHotnessDomainService, CreatorHotnessDomainService>();
    
    // Application Service
    services.AddScoped<ICreatorHotnessApplicationService, CreatorHotnessApplicationService>();
    
    // 事件处理器
    services.AddScoped<IDomainEventHandler<UserFollowedEvent>, UserFollowedCreatorHotnessHandler>();
    services.AddScoped<IDomainEventHandler<UserUnfollowedEvent>, UserUnfollowedCreatorHotnessHandler>();
    services.AddScoped<IDomainEventHandler<PostPublishedEvent>, PostPublishedCreatorHotnessHandler>();
    
    return services;
}
```
