# PostService 详细设计

## 服务概述

PostService 是文章上下文的核心服务，负责文章的完整生命周期管理，包括：
- 文章创建、发布、更新、删除
- 草稿管理
- 定时发布
- 文章查询（单篇、列表）

## 当前实现分析

### 依赖清单（12+ 依赖）

```csharp
public class PostService(
    AppDbContext dbContext,                    // 数据访问
    IMapper mapper,                            // 对象映射
    ILogger<PostService> logger,               // 日志
    IConnectionMultiplexer redis,              // Redis 缓存
    IOptions<PostCacheConfig> cacheConfig,     // 缓存配置
    IServiceScopeFactory serviceScopeFactory,  // 作用域工厂
    ISnowflakeIdService snowflakeIdService,    // ID 生成
    IPostHotnessService? hotnessService,       // 热度服务（跨上下文）
    IViewCountService? viewCountService,       // 阅读量服务
    IUserService? userService,                 // 用户服务（跨上下文）
    ITopicService? topicService,               // 话题服务（跨上下文）
    IEventPublisher? eventPublisher            // 事件发布
) : IPostService
```

### 问题分析

1. **跨上下文直接依赖**：
   - `IUserService` - 获取作者信息
   - `ITopicService` - 更新话题统计
   - `IPostHotnessService` - 更新热度

2. **职责过多**：
   - 同时处理 CRUD、统计、缓存、热度更新
   - 方法过长（如 `GetPostForReadByIdAsync` 超过 100 行）

3. **缓存逻辑散落**：
   - 部分在 PostService 内部
   - 部分在 CachedPostService 装饰器

## DDD 重构设计

### 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      PostController                              │
│                    (Presentation Layer)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  PostApplicationService                          │
│                    (Application Layer)                           │
│                                                                  │
│  依赖：                                                          │
│  - IPostRepository                                               │
│  - IPostDomainService                                            │
│  - IDomainEventDispatcher                                        │
│  - IUnitOfWork                                                   │
│  - IMapper                                                       │
│  - ILogger                                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PostDomainService                             │
│                      (Domain Layer)                              │
│                                                                  │
│  依赖：                                                          │
│  - ISnowflakeIdService                                           │
│  - ITopicRepository（验证话题存在）                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PostRepository                              │
│                   (Infrastructure Layer)                         │
│                                                                  │
│  依赖：                                                          │
│  - AppDbContext                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/IPostRepository.cs
public interface IPostRepository
{
    // 查询
    Task<Post?> GetByIdAsync(long postId);
    Task<Post?> GetByIdAndOwnerAsync(long postId, string ownerId);
    Task<Post?> GetForReadAsync(long postId);
    Task<IReadOnlyList<Post>> GetByIdsAsync(IEnumerable<long> postIds);
    Task<bool> ExistsAsync(long postId);
    
    // 列表查询
    Task<(IReadOnlyList<Post> Items, int Total)> GetPublishedPostsAsync(
        int page, int pageSize, string? sortBy = null);
    Task<(IReadOnlyList<Post> Items, int Total)> GetUserPostsAsync(
        string userId, int page, int pageSize, PostStatus? status = null);
    Task<(IReadOnlyList<Post> Items, int Total)> GetDraftsAsync(
        string userId, int page, int pageSize);
    
    // 写操作
    Task<Post> AddAsync(Post post);
    Task UpdateAsync(Post post);
    Task DeleteAsync(long postId);
    
    // 分类关联
    Task AddCategoriesAsync(long postId, IEnumerable<int> categoryIds);
    Task RemoveCategoriesAsync(long postId);
}
```

### Repository 实现

```csharp
// ZhiCoreCore/Infrastructure/Repositories/PostRepository.cs
public class PostRepository : IPostRepository
{
    private readonly AppDbContext _dbContext;
    
    public PostRepository(AppDbContext dbContext)
    {
        _dbContext = dbContext;
    }
    
    public async Task<Post?> GetByIdAsync(long postId)
    {
        return await _dbContext.Posts
            .AsNoTracking()
            .FirstOrDefaultAsync(p => p.Id == postId && !p.Deleted);
    }
    
    public async Task<Post?> GetByIdAndOwnerAsync(long postId, string ownerId)
    {
        return await _dbContext.Posts
            .AsTracking()
            .FirstOrDefaultAsync(p => p.Id == postId && p.OwnerId == ownerId && !p.Deleted);
    }
    
    public async Task<Post?> GetForReadAsync(long postId)
    {
        // 使用 Join 获取文章和作者信息
        return await (
            from p in _dbContext.Posts
            join u in _dbContext.Users on p.OwnerId equals u.Id
            where p.Id == postId && !p.Deleted
            select p
        ).FirstOrDefaultAsync();
    }
    
    public async Task<Post> AddAsync(Post post)
    {
        await _dbContext.Posts.AddAsync(post);
        await _dbContext.SaveChangesAsync();
        return post;
    }
    
    public async Task UpdateAsync(Post post)
    {
        _dbContext.Posts.Update(post);
        await _dbContext.SaveChangesAsync();
    }
    
    public async Task DeleteAsync(long postId)
    {
        await _dbContext.Posts
            .Where(p => p.Id == postId)
            .ExecuteUpdateAsync(s => s
                .SetProperty(p => p.Deleted, true)
                .SetProperty(p => p.UpdateTime, DateTimeOffset.UtcNow));
    }
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/IPostDomainService.cs
public interface IPostDomainService
{
    /// <summary>
    /// 创建文章实体（包含业务规则验证）
    /// </summary>
    Task<Post> CreatePostAsync(string userId, PublishPostReq req);
    
    /// <summary>
    /// 验证文章数据
    /// </summary>
    Task ValidatePostAsync(Post post);
    
    /// <summary>
    /// 判断是否为定时发布
    /// </summary>
    bool IsScheduledPublish(DateTimeOffset? publishAt);
}

// ZhiCoreCore/Domain/Services/PostDomainService.cs
public class PostDomainService : IPostDomainService
{
    private readonly ISnowflakeIdService _snowflakeIdService;
    private readonly ITopicRepository _topicRepository;
    
    public PostDomainService(
        ISnowflakeIdService snowflakeIdService,
        ITopicRepository topicRepository)
    {
        _snowflakeIdService = snowflakeIdService;
        _topicRepository = topicRepository;
    }
    
    public async Task<Post> CreatePostAsync(string userId, PublishPostReq req)
    {
        // 验证话题存在
        if (req.TopicId.HasValue)
        {
            var topicExists = await _topicRepository.ExistsAsync(req.TopicId.Value);
            if (!topicExists)
                throw new BusinessException(BusinessError.TopicNotFound);
        }
        
        var now = DateTimeOffset.UtcNow;
        var isScheduled = IsScheduledPublish(req.PublishAt);
        
        return new Post
        {
            Id = _snowflakeIdService.NextId(),
            OwnerId = userId,
            Title = req.Title,
            Raw = req.Raw,
            Html = req.Html,
            Excerpt = req.Excerpt,
            TopicId = req.TopicId,
            Format = (PostFormat)req.Format,
            Type = (PostType)req.Type,
            Visibility = (Visibility)req.Visibility,
            Tags = req.Tags,
            Status = isScheduled ? PostStatus.Scheduled : PostStatus.Published,
            PublishedAt = isScheduled ? req.PublishAt!.Value : now,
            CreateTime = now,
            UpdateTime = now
        };
    }
    
    public bool IsScheduledPublish(DateTimeOffset? publishAt)
    {
        return publishAt.HasValue && publishAt.Value > DateTimeOffset.UtcNow.AddSeconds(5);
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Post/IPostApplicationService.cs
public interface IPostApplicationService
{
    Task<long> PublishPostAsync(string userId, PublishPostReq req);
    Task<long> PublishDraftAsync(string userId, long draftId, PublishPostReq req);
    Task UpdatePostAsync(string userId, UpdatePostReq req);
    Task DeletePostAsync(string userId, long postId);
    Task<GetPostForReadVo?> GetPostForReadAsync(string? userId, long postId);
    Task<PaginatedResult<PostPreviewVo>> GetPostsAsync(PostQueryReq query);
    
    // 草稿管理
    Task SaveAsDraftAsync(string userId, UploadDraftReq req);
    Task<PaginatedResult<DraftPreviewVo>> GetDraftsAsync(string userId, PaginationRequest pagination);
}

// ZhiCoreCore/Application/Post/PostApplicationService.cs
public class PostApplicationService : IPostApplicationService
{
    private readonly IPostRepository _postRepository;
    private readonly IPostDomainService _postDomainService;
    private readonly IMapper _mapper;
    private readonly ILogger<PostApplicationService> _logger;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly IUnitOfWork _unitOfWork;
    
    public PostApplicationService(
        IPostRepository postRepository,
        IPostDomainService postDomainService,
        IMapper mapper,
        ILogger<PostApplicationService> logger,
        IDomainEventDispatcher eventDispatcher,
        IUnitOfWork unitOfWork)
    {
        _postRepository = postRepository;
        _postDomainService = postDomainService;
        _mapper = mapper;
        _logger = logger;
        _eventDispatcher = eventDispatcher;
        _unitOfWork = unitOfWork;
    }
    
    public async Task<long> PublishPostAsync(string userId, PublishPostReq req)
    {
        // 1. 通过 Domain Service 创建文章（包含业务规则验证）
        var post = await _postDomainService.CreatePostAsync(userId, req);
        
        // 2. 持久化（通过 UnitOfWork 管理事务）
        await _unitOfWork.BeginTransactionAsync();
        try
        {
            await _postRepository.AddAsync(post);
            
            // 添加分类关联
            if (req.CategoryIds?.Any() == true)
            {
                await _postRepository.AddCategoriesAsync(post.Id, req.CategoryIds);
            }
            
            await _unitOfWork.CommitAsync();
        }
        catch (Exception ex)
        {
            await _unitOfWork.RollbackAsync();
            _logger.LogError(ex, "发布文章失败");
            throw;
        }
        
        // 3. 发布领域事件（事务提交后）
        if (post.Status == PostStatus.Published)
        {
            await _eventDispatcher.DispatchAsync(new PostPublishedEvent
            {
                PostId = post.Id,
                AuthorId = userId,
                Title = post.Title,
                Excerpt = post.Excerpt,
                TopicId = post.TopicId,
                PublishedAt = post.PublishedAt
            });
        }
        
        _logger.LogInformation("文章发布成功，userId: {UserId}, postId: {PostId}", userId, post.Id);
        return post.Id;
    }
    
    public async Task UpdatePostAsync(string userId, UpdatePostReq req)
    {
        // 1. 通过 Repository 加载聚合根
        var post = await _postRepository.GetByIdAndOwnerAsync(req.Id, userId);
        if (post == null)
            throw new BusinessException(BusinessError.PostNotFound);
        
        var originalStatus = post.Status;
        
        // 2. 执行领域逻辑（在实体上）
        post.Title = req.Title;
        post.Raw = req.Raw;
        post.Html = req.Html;
        post.Excerpt = req.Excerpt;
        post.Format = (PostFormat)req.Format;
        post.Type = (PostType)req.Type;
        post.Visibility = (Visibility)req.Visibility;
        post.Tags = req.Tags;
        post.UpdateTime = DateTimeOffset.UtcNow;
        
        // 状态处理逻辑
        if (originalStatus == PostStatus.TakenDown)
        {
            post.Status = PostStatus.PendingReview;
        }
        else if (originalStatus == PostStatus.Draft)
        {
            post.Status = PostStatus.Published;
            post.PublishedAt = DateTimeOffset.UtcNow;
        }
        
        // 3. 持久化
        await _unitOfWork.BeginTransactionAsync();
        try
        {
            await _postRepository.RemoveCategoriesAsync(post.Id);
            if (req.CategoryIds?.Any() == true)
            {
                await _postRepository.AddCategoriesAsync(post.Id, req.CategoryIds);
            }
            await _postRepository.UpdateAsync(post);
            await _unitOfWork.CommitAsync();
        }
        catch (Exception ex)
        {
            await _unitOfWork.RollbackAsync();
            _logger.LogError(ex, "更新文章失败");
            throw;
        }
        
        // 4. 发布领域事件
        await _eventDispatcher.DispatchAsync(new PostUpdatedEvent
        {
            PostId = post.Id,
            AuthorId = userId,
            Title = post.Title,
            TopicId = post.TopicId
        });
    }
}
```

## 领域事件

### PostPublishedEvent

```csharp
public record PostPublishedEvent : DomainEventBase
{
    public override string EventType => nameof(PostPublishedEvent);
    
    public long PostId { get; init; }
    public string AuthorId { get; init; } = string.Empty;
    public string Title { get; init; } = string.Empty;
    public string? Excerpt { get; init; }
    public long? TopicId { get; init; }
    public DateTimeOffset PublishedAt { get; init; }
}
```

### 事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/Post/PostPublishedEventHandler.cs
public class PostPublishedEventHandler : IDomainEventHandler<PostPublishedEvent>
{
    private readonly IPostHotnessService _hotnessService;
    private readonly ISearchService _searchService;
    private readonly ILogger<PostPublishedEventHandler> _logger;
    
    public async Task HandleAsync(PostPublishedEvent @event, CancellationToken ct = default)
    {
        // 1. 初始化文章热度
        try
        {
            await _hotnessService.InitializePostHotnessAsync(@event.PostId);
            _logger.LogDebug("文章热度已初始化: PostId={PostId}", @event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "初始化文章热度失败: PostId={PostId}", @event.PostId);
        }
        
        // 2. 索引到搜索引擎
        try
        {
            await _searchService.IndexPostAsync(@event.PostId);
            _logger.LogDebug("文章已索引: PostId={PostId}", @event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "索引文章失败: PostId={PostId}", @event.PostId);
        }
    }
}
```

## 缓存策略

### Cached Repository

```csharp
// ZhiCoreCore/Infrastructure/Repositories/CachedPostRepository.cs
public class CachedPostRepository : IPostRepository
{
    private readonly IPostRepository _inner;
    private readonly IDatabase _redis;
    private readonly ILogger<CachedPostRepository> _logger;
    private readonly TimeSpan _defaultTtl = TimeSpan.FromMinutes(10);
    
    public async Task<Post?> GetByIdAsync(long postId)
    {
        var cacheKey = $"post:{postId}";
        
        // 1. 尝试从缓存获取
        var cached = await _redis.StringGetAsync(cacheKey);
        if (cached.HasValue)
        {
            return JsonSerializer.Deserialize<Post>(cached!);
        }
        
        // 2. 缓存未命中，从数据库获取
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
        await _inner.UpdateAsync(post);
        
        // 失效缓存
        var cacheKey = $"post:{post.Id}";
        await _redis.KeyDeleteAsync(cacheKey);
    }
}
```

### Redis Key 设计

| Key 模式 | 用途 | TTL |
|---------|------|-----|
| `post:{postId}` | 文章详情缓存 | 10 分钟 |
| `post:stats:{postId}` | 文章统计 Hash | 永久 |
| `post:list:published:{page}` | 已发布文章列表 | 5 分钟 |
| `user:{userId}:posts:{page}` | 用户文章列表 | 5 分钟 |

## 降级策略

### 数据库查询降级

当 Redis 不可用时，直接查询数据库：

```csharp
public async Task<Post?> GetByIdAsync(long postId)
{
    return await _redisPolicyProvider.ExecuteWithFallbackAsync(
        async _ =>
        {
            // 尝试从 Redis 获取
            var cached = await _redis.StringGetAsync($"post:{postId}");
            if (cached.HasValue)
                return JsonSerializer.Deserialize<Post>(cached!);
            
            // 回源数据库
            var post = await _inner.GetByIdAsync(postId);
            if (post != null)
            {
                await _redis.StringSetAsync($"post:{postId}", 
                    JsonSerializer.Serialize(post), _defaultTtl);
            }
            return post;
        },
        fallbackAction: async _ =>
        {
            _logger.LogWarning("Redis 不可用，降级到数据库查询");
            return await _inner.GetByIdAsync(postId);
        },
        operationKey: $"Post:GetById:{postId}");
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 12+ | 6 |
| 跨上下文依赖 | IUserService, ITopicService, IHotnessService | 无（通过事件解耦） |
| 缓存逻辑 | 散落在服务内部 | 集中在 CachedPostRepository |
| 事务管理 | 手动管理 | UnitOfWork 统一管理 |
