# SearchService 详细设计

## 服务概述

SearchService 负责全文搜索功能，包括：
- 文章搜索（标题、内容、标签）
- 用户搜索（昵称、简介）
- 话题搜索
- 搜索结果统计信息补充

## 当前实现分析

### 依赖清单（6 依赖）

```csharp
public class ElasticsearchSearchService(
    IElasticClient elasticClient,
    ILogger<ElasticsearchSearchService> logger,
    IConnectionMultiplexer redis,
    IPostStatsService postStatsService,
    IUserService userService,
    IOptions<ElasticsearchConfig> config
) : ISearchService
```

### 当前架构特点

1. **Elasticsearch 驱动**：使用 NEST 客户端
2. **统计信息补充**：搜索结果需要补充点赞数等统计
3. **中文分词**：使用 IK 分词器

### 问题分析

1. **跨上下文依赖**：依赖 IPostStatsService、IUserService
2. **无降级策略**：ES 不可用时直接失败
3. **索引更新分散**：各服务自行调用索引更新

## DDD 重构设计

### Application Service

```csharp
// ZhiCoreCore/Application/Search/ISearchApplicationService.cs
public interface ISearchApplicationService
{
    Task<SearchResult<PostSearchVo>> SearchPostsAsync(PostSearchQuery query);
    Task<SearchResult<UserSearchVo>> SearchUsersAsync(UserSearchQuery query);
    Task<SearchResult<TopicSearchVo>> SearchTopicsAsync(TopicSearchQuery query);
    Task<SearchSuggestion> GetSuggestionsAsync(string keyword);
}

// ZhiCoreCore/Application/Search/SearchApplicationService.cs
public class SearchApplicationService : ISearchApplicationService
{
    private readonly ISearchService _searchService;
    private readonly IPostStatsRepository _postStatsRepository;
    private readonly IUserRepository _userRepository;
    private readonly ILogger<SearchApplicationService> _logger;
    
    public async Task<SearchResult<PostSearchVo>> SearchPostsAsync(PostSearchQuery query)
    {
        // 1. 执行搜索
        var searchResult = await _searchService.SearchPostsAsync(query);
        
        if (!searchResult.Items.Any())
        {
            return new SearchResult<PostSearchVo>
            {
                Items = new List<PostSearchVo>(),
                Total = 0,
                Page = query.Page,
                PageSize = query.PageSize
            };
        }
        
        // 2. 批量获取统计信息
        var postIds = searchResult.Items.Select(p => p.Id).ToList();
        var stats = await _postStatsRepository.GetStatsBatchAsync(postIds);
        
        // 3. 批量获取作者信息
        var authorIds = searchResult.Items.Select(p => p.AuthorId).Distinct().ToList();
        var authors = await _userRepository.GetBasicInfoBatchAsync(authorIds);
        
        // 4. 组装结果
        var items = searchResult.Items.Select(p => new PostSearchVo
        {
            Id = p.Id,
            Title = p.Title,
            Excerpt = p.Excerpt,
            AuthorId = p.AuthorId,
            AuthorName = authors.TryGetValue(p.AuthorId, out var author) ? author.NickName : "未知用户",
            AuthorAvatar = author?.AvatarUrl,
            PublishedAt = p.PublishedAt,
            ViewCount = stats.TryGetValue(p.Id, out var stat) ? stat.ViewCount : 0,
            LikeCount = stat?.LikeCount ?? 0,
            CommentCount = stat?.CommentCount ?? 0,
            Tags = p.Tags,
            HighlightTitle = p.HighlightTitle,
            HighlightContent = p.HighlightContent
        }).ToList();
        
        return new SearchResult<PostSearchVo>
        {
            Items = items,
            Total = searchResult.Total,
            Page = query.Page,
            PageSize = query.PageSize
        };
    }
}
```

### 索引服务

```csharp
// ZhiCoreCore/Infrastructure/Search/ISearchIndexService.cs
public interface ISearchIndexService
{
    Task IndexPostAsync(long postId);
    Task UpdatePostIndexAsync(long postId);
    Task DeletePostIndexAsync(long postId);
    Task IndexUserAsync(string userId);
    Task BulkIndexPostsAsync(IEnumerable<long> postIds);
}

// ZhiCoreCore/Infrastructure/Search/SearchIndexService.cs
public class SearchIndexService : ISearchIndexService
{
    private readonly IElasticClient _elasticClient;
    private readonly IPostRepository _postRepository;
    private readonly IUserRepository _userRepository;
    private readonly ILogger<SearchIndexService> _logger;
    
    public async Task IndexPostAsync(long postId)
    {
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null || post.Status != PostStatus.Published)
        {
            _logger.LogWarning("文章不存在或未发布，跳过索引: PostId={PostId}", postId);
            return;
        }
        
        var author = await _userRepository.GetBasicInfoAsync(post.OwnerId);
        
        var document = new PostDocument
        {
            Id = post.Id,
            Title = post.Title,
            Content = StripHtml(post.Html),
            Excerpt = post.Excerpt,
            AuthorId = post.OwnerId,
            AuthorName = author?.NickName ?? "未知用户",
            Tags = post.Tags?.Split(',').ToList() ?? new List<string>(),
            TopicId = post.TopicId,
            Status = post.Status.ToString(),
            PublishedAt = post.PublishedAt,
            ViewCount = 0,
            LikeCount = 0,
            CommentCount = 0
        };
        
        var response = await _elasticClient.IndexDocumentAsync(document);
        
        if (!response.IsValid)
        {
            _logger.LogError("索引文章失败: PostId={PostId}, Error={Error}", 
                postId, response.OriginalException?.Message);
            throw new SearchIndexException($"索引文章失败: {postId}");
        }
        
        _logger.LogDebug("文章已索引: PostId={PostId}", postId);
    }
    
    public async Task DeletePostIndexAsync(long postId)
    {
        var response = await _elasticClient.DeleteAsync<PostDocument>(postId);
        
        if (!response.IsValid && response.Result != Result.NotFound)
        {
            _logger.LogError("删除文章索引失败: PostId={PostId}", postId);
        }
        
        _logger.LogDebug("文章索引已删除: PostId={PostId}", postId);
    }
}
```

## 领域事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/Search/PostPublishedSearchHandler.cs
public class PostPublishedSearchHandler : IDomainEventHandler<PostPublishedEvent>
{
    private readonly ISearchIndexService _searchIndexService;
    private readonly ILogger<PostPublishedSearchHandler> _logger;
    
    public async Task HandleAsync(PostPublishedEvent @event, CancellationToken ct = default)
    {
        try
        {
            await _searchIndexService.IndexPostAsync(@event.PostId);
            _logger.LogDebug("文章已索引到搜索引擎: PostId={PostId}", @event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "索引文章到搜索引擎失败: PostId={PostId}", @event.PostId);
            // 可以将失败的任务放入重试队列
        }
    }
}

// ZhiCoreCore/Domain/EventHandlers/Search/PostUpdatedSearchHandler.cs
public class PostUpdatedSearchHandler : IDomainEventHandler<PostUpdatedEvent>
{
    private readonly ISearchIndexService _searchIndexService;
    private readonly ILogger<PostUpdatedSearchHandler> _logger;
    
    public async Task HandleAsync(PostUpdatedEvent @event, CancellationToken ct = default)
    {
        try
        {
            await _searchIndexService.UpdatePostIndexAsync(@event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新文章索引失败: PostId={PostId}", @event.PostId);
        }
    }
}

// ZhiCoreCore/Domain/EventHandlers/Search/PostDeletedSearchHandler.cs
public class PostDeletedSearchHandler : IDomainEventHandler<PostDeletedEvent>
{
    private readonly ISearchIndexService _searchIndexService;
    private readonly ILogger<PostDeletedSearchHandler> _logger;
    
    public async Task HandleAsync(PostDeletedEvent @event, CancellationToken ct = default)
    {
        try
        {
            await _searchIndexService.DeletePostIndexAsync(@event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "删除文章索引失败: PostId={PostId}", @event.PostId);
        }
    }
}
```

## 降级策略

### 数据库降级搜索

```csharp
// ZhiCoreCore/Infrastructure/Search/DatabaseSearchService.cs
public class DatabaseSearchService : ISearchService
{
    private readonly AppDbContext _dbContext;
    
    public async Task<SearchResult<PostSearchItem>> SearchPostsAsync(PostSearchQuery query)
    {
        var queryable = _dbContext.Posts
            .Where(p => p.Status == PostStatus.Published && !p.Deleted);
        
        if (!string.IsNullOrEmpty(query.Keyword))
        {
            queryable = queryable.Where(p => 
                EF.Functions.ILike(p.Title, $"%{query.Keyword}%") ||
                EF.Functions.ILike(p.Raw, $"%{query.Keyword}%"));
        }
        
        var total = await queryable.CountAsync();
        
        var items = await queryable
            .OrderByDescending(p => p.PublishedAt)
            .Skip((query.Page - 1) * query.PageSize)
            .Take(query.PageSize)
            .Select(p => new PostSearchItem
            {
                Id = p.Id,
                Title = p.Title,
                Excerpt = p.Excerpt,
                AuthorId = p.OwnerId,
                PublishedAt = p.PublishedAt
            })
            .ToListAsync();
        
        return new SearchResult<PostSearchItem>
        {
            Items = items,
            Total = total,
            Page = query.Page,
            PageSize = query.PageSize
        };
    }
}
```

### 弹性搜索服务

```csharp
// ZhiCoreCore/Infrastructure/Search/ResilientSearchService.cs
public class ResilientSearchService : ISearchService
{
    private readonly ISearchService _primaryService;
    private readonly ISearchService _fallbackService;
    private readonly IResiliencePolicy _policy;
    private readonly ILogger<ResilientSearchService> _logger;
    
    public async Task<SearchResult<PostSearchItem>> SearchPostsAsync(PostSearchQuery query)
    {
        return await _policy.ExecuteWithFallbackAsync(
            primaryAction: async () =>
            {
                return await _primaryService.SearchPostsAsync(query);
            },
            fallbackAction: async () =>
            {
                _logger.LogWarning("Elasticsearch 不可用，降级到数据库搜索");
                return await _fallbackService.SearchPostsAsync(query);
            },
            operationKey: "SearchPosts");
    }
}
```

## 缓存策略

### 搜索结果缓存

```csharp
public async Task<SearchResult<PostSearchVo>> SearchPostsAsync(PostSearchQuery query)
{
    // 热门搜索词的结果可以缓存
    if (IsHotKeyword(query.Keyword))
    {
        var cacheKey = $"search:posts:{query.GetHashCode()}";
        var cached = await _redis.StringGetAsync(cacheKey);
        
        if (cached.HasValue)
        {
            return JsonSerializer.Deserialize<SearchResult<PostSearchVo>>(cached!);
        }
        
        var result = await ExecuteSearchAsync(query);
        
        // 缓存 5 分钟
        await _redis.StringSetAsync(cacheKey, 
            JsonSerializer.Serialize(result), 
            TimeSpan.FromMinutes(5));
        
        return result;
    }
    
    return await ExecuteSearchAsync(query);
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 6 | 4 |
| 索引更新 | 各服务直接调用 | 事件驱动 |
| 降级策略 | 无 | 数据库降级 |
| 统计补充 | 同步调用 | 批量获取 |
