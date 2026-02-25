# 缓存策略

## 概述

在 DDD 架构中，缓存属于基础设施层，有两种主要实现方式：
1. **Cached Repository** - Repository 内部集成缓存逻辑
2. **Cache Decorator** - 装饰器模式包装原有 Service

## 缓存层级

```
┌─────────────────────────────────────────────────────────────────┐
│                     Application Layer                            │
│         - 不直接操作缓存                                          │
│         - 通过 Repository 或 Cached Service 访问数据              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                           │
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐             │
│  │  Cached Repository  │    │   Cache Decorator   │             │
│  │  (方式一)           │    │   (方式二)          │             │
│  └─────────────────────┘    └─────────────────────┘             │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Redis / Memory Cache                      ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## 方式一：Cached Repository

### 适用场景

- 单实体查询（GetById）
- 列表查询（GetList）
- 读多写少的场景

### 实现示例

```csharp
// ZhiCoreCore/Infrastructure/Repositories/CachedPostRepository.cs
public class CachedPostRepository : IPostRepository
{
    private readonly IPostRepository _inner;
    private readonly IDatabase _redis;
    private readonly ILogger<CachedPostRepository> _logger;
    private readonly TimeSpan _defaultTtl = TimeSpan.FromMinutes(10);
    
    public CachedPostRepository(
        PostRepository inner,  // 注入具体实现
        IConnectionMultiplexer redis,
        ILogger<CachedPostRepository> logger)
    {
        _inner = inner;
        _redis = redis.GetDatabase();
        _logger = logger;
    }
    
    public async Task<Post?> GetByIdAsync(long postId)
    {
        var cacheKey = $"post:{postId}";
        
        // 1. 尝试从缓存获取
        var cached = await _redis.StringGetAsync(cacheKey);
        if (cached.HasValue)
        {
            _logger.LogDebug("缓存命中: {CacheKey}", cacheKey);
            return JsonSerializer.Deserialize<Post>(cached!);
        }
        
        // 2. 缓存未命中，从数据库获取
        _logger.LogDebug("缓存未命中: {CacheKey}", cacheKey);
        var post = await _inner.GetByIdAsync(postId);
        
        // 3. 写入缓存
        if (post != null)
        {
            await _redis.StringSetAsync(
                cacheKey, 
                JsonSerializer.Serialize(post), 
                _defaultTtl);
        }
        
        return post;
    }
    
    public async Task UpdateAsync(Post post)
    {
        // 1. 更新数据库
        await _inner.UpdateAsync(post);
        
        // 2. 失效缓存
        var cacheKey = $"post:{post.Id}";
        await _redis.KeyDeleteAsync(cacheKey);
        _logger.LogDebug("缓存已失效: {CacheKey}", cacheKey);
    }
    
    public async Task DeleteAsync(long postId)
    {
        // 1. 删除数据库记录
        await _inner.DeleteAsync(postId);
        
        // 2. 失效缓存
        var cacheKey = $"post:{postId}";
        await _redis.KeyDeleteAsync(cacheKey);
    }
    
    // 其他方法委托给 _inner
    public Task<Post> AddAsync(Post post) => _inner.AddAsync(post);
    public Task<bool> ExistsAsync(long postId) => _inner.ExistsAsync(postId);
}
```

### DI 注册

```csharp
// ZhiCoreCore/Extensions/RepositoryServiceExtensions.cs
public static class RepositoryServiceExtensions
{
    public static IServiceCollection AddRepositories(this IServiceCollection services)
    {
        // 注册基础 Repository
        services.AddScoped<PostRepository>();
        
        // 注册带缓存的 Repository（装饰器模式）
        services.AddScoped<IPostRepository>(sp =>
        {
            var inner = sp.GetRequiredService<PostRepository>();
            var redis = sp.GetRequiredService<IConnectionMultiplexer>();
            var logger = sp.GetRequiredService<ILogger<CachedPostRepository>>();
            return new CachedPostRepository(inner, redis, logger);
        });
        
        return services;
    }
}
```

## 方式二：Cache Decorator

### 适用场景

- 复杂业务逻辑
- 需要缓存整个服务方法的结果
- 保留现有 Cached*Service 实现

### 实现示例

```csharp
// ZhiCoreCore/Infrastructure/Caching/CachedPostService.cs
public class CachedPostService : IPostService
{
    private readonly IPostService _inner;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly ILogger<CachedPostService> _logger;
    
    public CachedPostService(
        IPostService inner,
        IConnectionMultiplexer redis,
        IRedisPolicyProvider redisPolicyProvider,
        ILogger<CachedPostService> logger)
    {
        _inner = inner;
        _redis = redis.GetDatabase();
        _redisPolicyProvider = redisPolicyProvider;
        _logger = logger;
    }
    
    public async Task<GetPostForReadVo?> GetPostForReadByIdAsync(string? userId, long postId)
    {
        var cacheKey = $"post:read:{postId}";
        
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                // 尝试从缓存获取
                var cached = await _redis.StringGetAsync(cacheKey);
                if (cached.HasValue)
                {
                    var result = JsonSerializer.Deserialize<GetPostForReadVo>(cached!);
                    // 用户相关字段需要实时查询
                    if (!string.IsNullOrEmpty(userId) && result != null)
                    {
                        result.IsLiked = await CheckUserLikedAsync(postId, userId);
                        result.IsFavorited = await CheckUserFavoritedAsync(postId, userId);
                    }
                    return result;
                }
                
                // 缓存未命中
                var post = await _inner.GetPostForReadByIdAsync(userId, postId);
                
                // 写入缓存（不包含用户相关字段）
                if (post != null)
                {
                    var cacheData = post with { IsLiked = false, IsFavorited = false };
                    await _redis.StringSetAsync(
                        cacheKey, 
                        JsonSerializer.Serialize(cacheData), 
                        TimeSpan.FromMinutes(10));
                }
                
                return post;
            },
            fallbackAction: async _ =>
            {
                _logger.LogWarning("Redis 不可用，降级到直接查询");
                return await _inner.GetPostForReadByIdAsync(userId, postId);
            },
            operationKey: $"Post:GetForRead:{postId}");
    }
    
    // 写操作：委托给 inner 并失效缓存
    public async Task<long> PublishPostAsync(string userId, PublishPostReq req)
    {
        var postId = await _inner.PublishPostAsync(userId, req);
        
        // 失效用户文章列表缓存
        await InvalidateUserPostsCacheAsync(userId);
        
        return postId;
    }
    
    public async Task UpdatePostAsync(UpdatePostReq req)
    {
        await _inner.UpdatePostAsync(req);
        
        // 失效文章缓存
        await _redis.KeyDeleteAsync($"post:read:{req.Id}");
        await _redis.KeyDeleteAsync($"post:{req.Id}");
    }
}
```

## 缓存策略选择

| 场景 | 推荐方式 | 说明 |
|------|---------|------|
| 单实体查询（GetById） | Cached Repository | 缓存粒度细，易于失效 |
| 列表查询（GetList） | Cached Repository | 可以缓存查询结果 |
| 复杂业务逻辑 | Cache Decorator | 缓存整个服务方法的结果 |
| 统计数据（点赞数、评论数） | Redis 原子操作 | 直接使用 Redis INCR/DECR |
| 热点数据（排行榜） | 专用缓存服务 | 如 HotnessResultCache |

## Redis Key 设计规范

### 命名规范

```
{domain}:{entity}:{id}:{field}
```

### 示例

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `post:{postId}` | String | 文章详情 | 10 分钟 |
| `post:stats:{postId}` | Hash | 文章统计 | 永久 |
| `post:likes:{postId}` | Set | 点赞用户集合 | 永久 |
| `user:{userId}` | String | 用户信息 | 30 分钟 |
| `user:following:{userId}` | Set | 关注列表 | 永久 |
| `comment:{commentId}` | String | 评论详情 | 10 分钟 |
| `session:{sessionId}` | String | 会话信息 | 7 天 |

## 缓存一致性

### 写操作策略

1. **Cache-Aside（旁路缓存）**
   - 写数据库
   - 删除缓存
   - 下次读取时重新加载

```csharp
public async Task UpdateAsync(Post post)
{
    // 1. 更新数据库
    await _dbContext.SaveChangesAsync();
    
    // 2. 删除缓存
    await _redis.KeyDeleteAsync($"post:{post.Id}");
}
```

2. **Write-Through（写穿透）**
   - 同时写数据库和缓存
   - 适用于写后立即读的场景

```csharp
public async Task UpdateAsync(Post post)
{
    // 1. 更新数据库
    await _dbContext.SaveChangesAsync();
    
    // 2. 更新缓存
    await _redis.StringSetAsync(
        $"post:{post.Id}", 
        JsonSerializer.Serialize(post), 
        _defaultTtl);
}
```

### 数据对账

定期任务比对 Redis 和数据库，修复不一致：

```csharp
// ZhiCoreCore/Services/Background/CacheReconciliationService.cs
public class CacheReconciliationService : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            await ReconcilePostStatsAsync();
            await Task.Delay(TimeSpan.FromMinutes(30), stoppingToken);
        }
    }
    
    private async Task ReconcilePostStatsAsync()
    {
        // 1. 获取所有有统计数据的文章ID
        // 2. 比对 Redis 和数据库的值
        // 3. 以数据库为准修复 Redis
        // 4. 记录修复日志
    }
}
```

## 缓存穿透防护

### 空值缓存

```csharp
public async Task<Post?> GetByIdAsync(long postId)
{
    var cacheKey = $"post:{postId}";
    var cached = await _redis.StringGetAsync(cacheKey);
    
    if (cached.HasValue)
    {
        if (cached == "NULL")
        {
            return null; // 空值缓存
        }
        return JsonSerializer.Deserialize<Post>(cached!);
    }
    
    var post = await _inner.GetByIdAsync(postId);
    
    if (post == null)
    {
        // 缓存空值，防止穿透
        await _redis.StringSetAsync(cacheKey, "NULL", TimeSpan.FromMinutes(5));
    }
    else
    {
        await _redis.StringSetAsync(cacheKey, JsonSerializer.Serialize(post), _defaultTtl);
    }
    
    return post;
}
```

### 布隆过滤器

```csharp
// 使用布隆过滤器预判断
public async Task<Post?> GetByIdAsync(long postId)
{
    // 1. 布隆过滤器检查
    if (!await _bloomFilter.ExistsAsync($"post:{postId}"))
    {
        return null; // 一定不存在
    }
    
    // 2. 正常缓存逻辑
    // ...
}
```

## 缓存雪崩防护

### 随机 TTL

```csharp
private TimeSpan GetRandomTtl(TimeSpan baseTtl)
{
    var random = new Random();
    var jitter = random.Next(0, 60); // 0-60 秒随机抖动
    return baseTtl.Add(TimeSpan.FromSeconds(jitter));
}

await _redis.StringSetAsync(cacheKey, data, GetRandomTtl(TimeSpan.FromMinutes(10)));
```

### 多级缓存

```csharp
public async Task<Post?> GetByIdAsync(long postId)
{
    // 1. L1 缓存（内存）
    if (_memoryCache.TryGetValue($"post:{postId}", out Post? cached))
    {
        return cached;
    }
    
    // 2. L2 缓存（Redis）
    var redisCached = await _redis.StringGetAsync($"post:{postId}");
    if (redisCached.HasValue)
    {
        var post = JsonSerializer.Deserialize<Post>(redisCached!);
        _memoryCache.Set($"post:{postId}", post, TimeSpan.FromSeconds(30));
        return post;
    }
    
    // 3. 数据库
    var dbPost = await _inner.GetByIdAsync(postId);
    if (dbPost != null)
    {
        _memoryCache.Set($"post:{postId}", dbPost, TimeSpan.FromSeconds(30));
        await _redis.StringSetAsync($"post:{postId}", JsonSerializer.Serialize(dbPost), _defaultTtl);
    }
    
    return dbPost;
}
```
