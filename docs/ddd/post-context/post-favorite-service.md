# PostFavoriteService 详细设计

## 服务概述

PostFavoriteService 负责文章收藏功能，包括：
- 收藏/取消收藏
- 收藏状态查询
- 用户收藏列表
- 批量获取收藏数

## 当前实现分析

### 依赖清单（8 依赖）

```csharp
public class PostFavoriteService(
    AppDbContext dbContext,
    IConnectionMultiplexer redis,
    ILogger<PostFavoriteService> logger,
    IServiceScopeFactory serviceScopeFactory,
    IAntiSpamService antiSpamService,
    IUserBlockService userBlockService,
    IEventPublisher eventPublisher,
    IRabbitMqPolicyProvider rabbitMqPolicyProvider
) : IPostFavoriteService
```

### 当前架构特点

1. **数据库优先**：收藏记录直接写入数据库
2. **Redis 缓存统计**：收藏数存储在 Redis Hash 中
3. **MQ 异步通知**：通过 RabbitMQ 发送收藏通知

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IPostFavoriteRepository.cs
public interface IPostFavoriteRepository
{
    /// <summary>
    /// 添加收藏
    /// </summary>
    Task<bool> AddFavoriteAsync(long postId, string userId);
    
    /// <summary>
    /// 取消收藏
    /// </summary>
    Task<bool> RemoveFavoriteAsync(long postId, string userId);
    
    /// <summary>
    /// 检查是否已收藏
    /// </summary>
    Task<bool> IsFavoritedAsync(long postId, string userId);
    
    /// <summary>
    /// 批量检查收藏状态
    /// </summary>
    Task<Dictionary<long, bool>> IsFavoritedBatchAsync(IEnumerable<long> postIds, string userId);
    
    /// <summary>
    /// 获取收藏数
    /// </summary>
    Task<int> GetFavoriteCountAsync(long postId);
    
    /// <summary>
    /// 批量获取收藏数
    /// </summary>
    Task<Dictionary<long, int>> GetFavoriteCountsBatchAsync(IEnumerable<long> postIds);
    
    /// <summary>
    /// 获取用户收藏的文章ID列表
    /// </summary>
    Task<(IReadOnlyList<long> PostIds, int Total)> GetUserFavoritePostIdsAsync(
        string userId, int page, int pageSize);
}
```

### Application Service

```csharp
// BlogCore/Application/Post/IPostFavoriteApplicationService.cs
public interface IPostFavoriteApplicationService
{
    Task FavoritePostAsync(long postId, string userId);
    Task UnfavoritePostAsync(long postId, string userId);
    Task<bool> IsPostFavoritedByUserAsync(long postId, string userId);
    Task<PaginatedResult<FavoritePostSummaryVo>> GetUserFavoritesAsync(
        string userId, int page, int pageSize);
}

// BlogCore/Application/Post/PostFavoriteApplicationService.cs
public class PostFavoriteApplicationService : IPostFavoriteApplicationService
{
    private readonly IPostRepository _postRepository;
    private readonly IPostFavoriteRepository _postFavoriteRepository;
    private readonly IAntiSpamService _antiSpamService;
    private readonly IUserBlockService _userBlockService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<PostFavoriteApplicationService> _logger;
    
    public async Task FavoritePostAsync(long postId, string userId)
    {
        // 1. 防刷检测
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Favorite, userId, postId.ToString());
        if (antiSpamResult.IsBlocked)
            throw BusinessException.CreateAntiSpamException(BusinessError.AntiSpamLimit, antiSpamResult);
        
        // 2. 获取文章信息
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null || post.Status != PostStatus.Published)
            throw new BusinessException(BusinessError.PostNotFound);
        
        // 3. 检查拉黑状态
        if (userId != post.OwnerId)
        {
            var isBlocked = await _userBlockService.IsBlockedByAsync(userId, post.OwnerId);
            if (isBlocked)
                throw new BusinessException(BusinessError.CannotFavoriteBlockedPost);
        }
        
        // 4. 执行收藏
        var favorited = await _postFavoriteRepository.AddFavoriteAsync(postId, userId);
        if (!favorited)
            throw new BusinessException(BusinessError.PostAlreadyFavorited);
        
        // 5. 记录操作
        await _antiSpamService.RecordActionAsync(
            AntiSpamActionType.Favorite, userId, postId.ToString());
        
        // 6. 发布领域事件
        await _eventDispatcher.DispatchAsync(new PostFavoritedEvent
        {
            PostId = postId,
            UserId = userId,
            AuthorId = post.OwnerId,
            PostTitle = post.Title,
            IsFavorite = true
        });
        
        _logger.LogDebug("用户 {UserId} 收藏文章 {PostId} 成功", userId, postId);
    }
    
    public async Task UnfavoritePostAsync(long postId, string userId)
    {
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null)
            throw new BusinessException(BusinessError.PostNotFound);
        
        var unfavorited = await _postFavoriteRepository.RemoveFavoriteAsync(postId, userId);
        if (!unfavorited)
            throw new BusinessException(BusinessError.PostNotFavorited);
        
        await _eventDispatcher.DispatchAsync(new PostFavoritedEvent
        {
            PostId = postId,
            UserId = userId,
            AuthorId = post.OwnerId,
            PostTitle = post.Title,
            IsFavorite = false
        });
    }
    
    public async Task<PaginatedResult<FavoritePostSummaryVo>> GetUserFavoritesAsync(
        string userId, int page, int pageSize)
    {
        var (postIds, total) = await _postFavoriteRepository
            .GetUserFavoritePostIdsAsync(userId, page, pageSize);
        
        if (!postIds.Any())
        {
            return new PaginatedResult<FavoritePostSummaryVo>
            {
                Items = new List<FavoritePostSummaryVo>(),
                Total = 0,
                Page = page,
                PageSize = pageSize,
                TotalPages = 0
            };
        }
        
        // 批量获取文章信息
        var posts = await _postRepository.GetByIdsAsync(postIds);
        
        // 转换为 VO
        var items = posts.Select(p => new FavoritePostSummaryVo
        {
            Id = p.Id,
            Title = p.Title,
            Excerpt = p.Excerpt,
            // ... 其他字段
        }).ToList();
        
        return new PaginatedResult<FavoritePostSummaryVo>
        {
            Items = items,
            Total = total,
            Page = page,
            PageSize = pageSize,
            TotalPages = (int)Math.Ceiling((double)total / pageSize)
        };
    }
}
```

## 领域事件

### PostFavoritedEvent

```csharp
public record PostFavoritedEvent : DomainEventBase
{
    public override string EventType => nameof(PostFavoritedEvent);
    
    public long PostId { get; init; }
    public string UserId { get; init; } = string.Empty;
    public string AuthorId { get; init; } = string.Empty;
    public string PostTitle { get; init; } = string.Empty;
    public bool IsFavorite { get; init; } // true=收藏, false=取消收藏
}
```

### 事件处理器

```csharp
public class PostFavoritedEventHandler : IDomainEventHandler<PostFavoritedEvent>
{
    private readonly INotificationService _notificationService;
    private readonly ILogger<PostFavoritedEventHandler> _logger;
    
    public async Task HandleAsync(PostFavoritedEvent @event, CancellationToken ct = default)
    {
        // 发送收藏通知（不通知自己）
        if (@event.IsFavorite && @event.UserId != @event.AuthorId)
        {
            try
            {
                await _notificationService.NotifyPostFavoritedAsync(
                    @event.AuthorId,
                    @event.PostId,
                    @event.PostTitle,
                    @event.UserId);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "发送收藏通知失败: PostId={PostId}", @event.PostId);
            }
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `post:stats:{postId}` | Hash | 存储收藏数（favorite_count 字段） | 永久 |
| `user:{userId}:favorites` | ZSet | 用户收藏列表（按时间排序） | 永久 |

### 缓存更新策略

1. **收藏时**：
   - 原子递增 `post:stats:{postId}` 的 `favorite_count`
   - 添加到 `user:{userId}:favorites` ZSet

2. **取消收藏时**：
   - 原子递减 `post:stats:{postId}` 的 `favorite_count`
   - 从 `user:{userId}:favorites` ZSet 移除

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 8 | 6 |
| 通知发送 | 直接调用 MQ | 事件处理器异步发送 |
| 缓存逻辑 | 散落在服务中 | 集中在 Repository |
