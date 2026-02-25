# CommentService 详细设计

## 服务概述

CommentService 负责评论的完整生命周期管理，包括：
- 评论创建（顶级评论、回复评论）
- 评论更新、删除
- 评论查询（分页、嵌套）

## API 端点映射

| HTTP 方法 | 路由 | 方法 | 说明 |
|-----------|------|------|------|
| POST | `/api/comments` | CreateCommentAsync | 创建评论 |
| PUT | `/api/comments/{id}` | UpdateCommentAsync | 更新评论 |
| DELETE | `/api/comments/{id}` | DeleteCommentAsync | 删除评论 |
| GET | `/api/posts/{postId}/comments` | GetCommentsAsync | 获取文章评论列表 |
| GET | `/api/comments/{id}` | GetCommentByIdAsync | 获取评论详情 |
| GET | `/api/comments/{id}/replies` | GetRepliesAsync | 获取评论回复列表 |

## 当前实现分析

### 依赖清单（10 依赖）

```csharp
public class CommentService(
    AppDbContext context,
    ILogger<CommentService> logger,
    ICommentReplyStatsService commentReplyStatsService,
    ICommentStatsService commentStatsService,
    IServiceScopeFactory serviceScopeFactory,
    IAntiSpamService antiSpamService,
    IUserBlockService userBlockService,
    ISnowflakeIdService snowflakeIdService,
    IEventPublisher eventPublisher,
    IRabbitMqPolicyProvider rabbitMqPolicyProvider
) : ICommentService
```

### 当前架构特点

1. **MQ 异步写库**：评论通过 RabbitMQ 批量写入数据库
2. **Polly 降级**：MQ 不可用时降级到直接写数据库
3. **原子统计更新**：使用 ExecuteUpdateAsync 原子更新评论数

### 问题分析

1. **跨上下文依赖**：通过 IServiceScopeFactory 获取 INotificationService
2. **降级逻辑复杂**：MQ 降级逻辑与主逻辑混在一起
3. **方法过长**：CreateCommentAsync 超过 200 行

## DDD 重构设计

### Repository 接口

```csharp
// ZhiCoreCore/Domain/Repositories/ICommentRepository.cs
public interface ICommentRepository
{
    /// <summary>
    /// 根据ID获取评论
    /// </summary>
    Task<Comment?> GetByIdAsync(long commentId);
    
    /// <summary>
    /// 获取文章的评论列表
    /// </summary>
    Task<(IReadOnlyList<CommentWithAuthor> Items, int Total)> GetByPostIdAsync(
        long postId, int page, int pageSize, string sortBy = "createtime", string sortOrder = "desc");
    
    /// <summary>
    /// 获取顶级评论列表
    /// </summary>
    Task<(IReadOnlyList<CommentWithAuthor> Items, int Total)> GetTopLevelCommentsAsync(
        long postId, int page, int pageSize, string sortBy = "createtime", string sortOrder = "desc");
    
    /// <summary>
    /// 获取评论的回复列表
    /// </summary>
    Task<(IReadOnlyList<CommentWithAuthor> Items, int Total)> GetRepliesAsync(
        long rootCommentId, int page, int pageSize);
    
    /// <summary>
    /// 添加评论
    /// </summary>
    Task<Comment> AddAsync(Comment comment);
    
    /// <summary>
    /// 更新评论
    /// </summary>
    Task UpdateAsync(Comment comment);
    
    /// <summary>
    /// 软删除评论
    /// </summary>
    Task DeleteAsync(long commentId);
    
    /// <summary>
    /// 获取评论数量
    /// </summary>
    Task<int> GetCountByPostIdAsync(long postId);
    
    /// <summary>
    /// 获取回复数量
    /// </summary>
    Task<int> GetReplyCountAsync(long rootCommentId);
}
```

### Repository 实现

```csharp
// ZhiCoreCore/Infrastructure/Repositories/CommentRepository.cs
public class CommentRepository : ICommentRepository
{
    private readonly AppDbContext _dbContext;
    private readonly ILogger<CommentRepository> _logger;
    
    public CommentRepository(AppDbContext dbContext, ILogger<CommentRepository> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }
    
    public async Task<Comment?> GetByIdAsync(long commentId)
    {
        return await _dbContext.Comments
            .AsNoTracking()
            .FirstOrDefaultAsync(c => c.Id == commentId && !c.Deleted);
    }
    
    public async Task<(IReadOnlyList<CommentWithAuthor> Items, int Total)> GetByPostIdAsync(
        long postId, int page, int pageSize, string sortBy = "createtime", string sortOrder = "desc")
    {
        var query = from c in _dbContext.Comments
                    join u in _dbContext.Users on c.AuthorId equals u.Id
                    where c.PostId == postId && !c.Deleted
                    select new CommentWithAuthor
                    {
                        Comment = c,
                        AuthorNickName = u.NickName,
                        AuthorAvatarUrl = u.AvatarUrl
                    };
        
        // 排序
        query = sortBy.ToLower() switch
        {
            "likes" => sortOrder == "asc" 
                ? query.OrderBy(x => x.Comment.LikeCount) 
                : query.OrderByDescending(x => x.Comment.LikeCount),
            _ => sortOrder == "asc" 
                ? query.OrderBy(x => x.Comment.CreateTime) 
                : query.OrderByDescending(x => x.Comment.CreateTime)
        };
        
        var total = await query.CountAsync();
        var items = await query
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();
        
        return (items, total);
    }
    
    public async Task<(IReadOnlyList<CommentWithAuthor> Items, int Total)> GetTopLevelCommentsAsync(
        long postId, int page, int pageSize, string sortBy = "createtime", string sortOrder = "desc")
    {
        var query = from c in _dbContext.Comments
                    join u in _dbContext.Users on c.AuthorId equals u.Id
                    where c.PostId == postId && c.ParentId == null && !c.Deleted
                    select new CommentWithAuthor
                    {
                        Comment = c,
                        AuthorNickName = u.NickName,
                        AuthorAvatarUrl = u.AvatarUrl
                    };
        
        // 排序逻辑同上
        query = sortBy.ToLower() switch
        {
            "likes" => sortOrder == "asc" 
                ? query.OrderBy(x => x.Comment.LikeCount) 
                : query.OrderByDescending(x => x.Comment.LikeCount),
            _ => sortOrder == "asc" 
                ? query.OrderBy(x => x.Comment.CreateTime) 
                : query.OrderByDescending(x => x.Comment.CreateTime)
        };
        
        var total = await query.CountAsync();
        var items = await query
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();
        
        return (items, total);
    }
    
    public async Task<(IReadOnlyList<CommentWithAuthor> Items, int Total)> GetRepliesAsync(
        long rootCommentId, int page, int pageSize)
    {
        var query = from c in _dbContext.Comments
                    join u in _dbContext.Users on c.AuthorId equals u.Id
                    where c.RootId == rootCommentId && c.Id != rootCommentId && !c.Deleted
                    orderby c.CreateTime
                    select new CommentWithAuthor
                    {
                        Comment = c,
                        AuthorNickName = u.NickName,
                        AuthorAvatarUrl = u.AvatarUrl
                    };
        
        var total = await query.CountAsync();
        var items = await query
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();
        
        return (items, total);
    }
    
    public async Task<Comment> AddAsync(Comment comment)
    {
        await _dbContext.Comments.AddAsync(comment);
        await _dbContext.SaveChangesAsync();
        return comment;
    }
    
    public async Task UpdateAsync(Comment comment)
    {
        _dbContext.Comments.Update(comment);
        await _dbContext.SaveChangesAsync();
    }
    
    public async Task DeleteAsync(long commentId)
    {
        await _dbContext.Comments
            .Where(c => c.Id == commentId)
            .ExecuteUpdateAsync(s => s
                .SetProperty(c => c.Deleted, true)
                .SetProperty(c => c.UpdateTime, DateTimeOffset.UtcNow));
    }
    
    public async Task<int> GetCountByPostIdAsync(long postId)
    {
        return await _dbContext.Comments
            .CountAsync(c => c.PostId == postId && !c.Deleted);
    }
    
    public async Task<int> GetReplyCountAsync(long rootCommentId)
    {
        return await _dbContext.Comments
            .CountAsync(c => c.RootId == rootCommentId && c.Id != rootCommentId && !c.Deleted);
    }
}
```

### Domain Service

```csharp
// ZhiCoreCore/Domain/Services/ICommentDomainService.cs
public interface ICommentDomainService
{
    /// <summary>
    /// 创建评论实体
    /// </summary>
    Task<Comment> CreateCommentAsync(CreateCommentReq dto, string userId, string? ipAddress = null);
    
    /// <summary>
    /// 验证评论操作
    /// </summary>
    Task ValidateCommentAsync(CreateCommentReq dto, string userId);
}

// ZhiCoreCore/Domain/Services/CommentDomainService.cs
public class CommentDomainService : ICommentDomainService
{
    private readonly ISnowflakeIdService _snowflakeIdService;
    private readonly IPostRepository _postRepository;
    private readonly ICommentRepository _commentRepository;
    private readonly IUserBlockService _userBlockService;
    
    public async Task<Comment> CreateCommentAsync(CreateCommentReq dto, string userId, string? ipAddress = null)
    {
        // 验证评论操作
        await ValidateCommentAsync(dto, userId);
        
        // 获取父评论信息
        Comment? parentComment = null;
        if (dto.ParentId.HasValue)
        {
            parentComment = await _commentRepository.GetByIdAsync(dto.ParentId.Value);
            if (parentComment == null || parentComment.Deleted)
                throw new BusinessException(BusinessError.ParentCommentNotExist);
            
            if (parentComment.PostId != dto.PostId)
                throw new BusinessException(BusinessError.ParentCommentNotBelong2Post);
        }
        
        var commentId = _snowflakeIdService.NextId();
        var now = DateTimeOffset.UtcNow;
        
        return new Comment
        {
            Id = commentId,
            Content = dto.Content,
            PostId = dto.PostId,
            AuthorId = userId,
            ParentId = dto.ParentId,
            RootId = parentComment?.RootId ?? commentId,
            IpAddress = ipAddress,
            CreateTime = now,
            UpdateTime = now
        };
    }
    
    public async Task ValidateCommentAsync(CreateCommentReq dto, string userId)
    {
        // 1. 验证文章存在且已发布
        var post = await _postRepository.GetByIdAsync(dto.PostId);
        if (post == null || post.Status != PostStatus.Published)
            throw new BusinessException(BusinessError.PostNotFound);
        
        // 2. 检查拉黑状态
        if (userId != post.OwnerId)
        {
            var isBlockedBy = await _userBlockService.IsBlockedByAsync(userId, post.OwnerId);
            if (isBlockedBy)
                throw new BusinessException(BusinessError.BlockedByPostOwner);
            
            var hasBlocked = await _userBlockService.IsBlockedAsync(userId, post.OwnerId);
            if (hasBlocked)
                throw new BusinessException(BusinessError.HasBlockedPostOwner);
        }
    }
}
```

### Application Service

```csharp
// ZhiCoreCore/Application/Comment/ICommentApplicationService.cs
public interface ICommentApplicationService
{
    Task<CommentVo> CreateCommentAsync(CreateCommentReq dto, string userId, string? ipAddress = null);
    Task<CommentVo> UpdateCommentAsync(long commentId, UpdateCommentReq dto, string userId);
    Task DeleteCommentAsync(long commentId, string userId);
    Task<CommentListVo> GetCommentsAsync(CommentQueryReq query, string? currentUserId = null);
    Task<CommentVo> GetCommentByIdAsync(long commentId, string? currentUserId = null);
}

// ZhiCoreCore/Application/Comment/CommentApplicationService.cs
public class CommentApplicationService : ICommentApplicationService
{
    private readonly IPostRepository _postRepository;
    private readonly ICommentRepository _commentRepository;
    private readonly ICommentDomainService _commentDomainService;
    private readonly IAntiSpamService _antiSpamService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly IMapper _mapper;
    private readonly ILogger<CommentApplicationService> _logger;
    
    public async Task<CommentVo> CreateCommentAsync(CreateCommentReq dto, string userId, string? ipAddress = null)
    {
        // 1. 防刷检测
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Comment, userId, dto.PostId.ToString(), ipAddress);
        if (antiSpamResult.IsBlocked)
            throw BusinessException.CreateAntiSpamException(BusinessError.AntiSpamLimit, antiSpamResult);
        
        // 2. 获取文章信息（用于事件）
        var post = await _postRepository.GetByIdAsync(dto.PostId);
        if (post == null)
            throw new BusinessException(BusinessError.PostNotFound);
        
        // 3. 通过 Domain Service 创建评论
        var comment = await _commentDomainService.CreateCommentAsync(dto, userId, ipAddress);
        
        // 4. 持久化
        await _commentRepository.AddAsync(comment);
        
        // 5. 记录操作
        await _antiSpamService.RecordActionAsync(
            AntiSpamActionType.Comment, userId, dto.PostId.ToString(), ipAddress);
        
        // 6. 发布领域事件
        await _eventDispatcher.DispatchAsync(new CommentCreatedEvent
        {
            CommentId = comment.Id,
            PostId = dto.PostId,
            PostTitle = post.Title,
            AuthorId = userId,
            PostOwnerId = post.OwnerId,
            ParentCommentId = dto.ParentId,
            ParentCommentAuthorId = dto.ParentId.HasValue 
                ? (await _commentRepository.GetByIdAsync(dto.ParentId.Value))?.AuthorId 
                : null,
            Content = dto.Content
        });
        
        _logger.LogInformation("评论创建成功: CommentId={CommentId}, PostId={PostId}", comment.Id, dto.PostId);
        
        return _mapper.Map<CommentVo>(comment);
    }
    
    public async Task DeleteCommentAsync(long commentId, string userId)
    {
        // 1. 获取评论
        var comment = await _commentRepository.GetByIdAsync(commentId);
        if (comment == null)
            throw new BusinessException(BusinessError.CommentNotFound);
        
        // 2. 获取文章信息（检查权限）
        var post = await _postRepository.GetByIdAsync(comment.PostId);
        
        // 3. 检查权限
        if (comment.AuthorId != userId && post?.OwnerId != userId)
            throw new BusinessException(BusinessError.InsufficientPermission);
        
        // 4. 计算需要减少的评论数
        int decrementCount = 1;
        if (comment.ParentId == null)
        {
            // 根评论：统计其下所有回复
            decrementCount += await _commentRepository.GetReplyCountAsync(commentId);
        }
        
        // 5. 删除评论
        await _commentRepository.DeleteAsync(commentId);
        
        // 6. 发布领域事件
        await _eventDispatcher.DispatchAsync(new CommentDeletedEvent
        {
            CommentId = commentId,
            PostId = comment.PostId,
            DecrementCount = decrementCount
        });
        
        _logger.LogInformation("评论删除成功: CommentId={CommentId}", commentId);
    }
}
```

## 领域事件

### CommentCreatedEvent

```csharp
public record CommentCreatedEvent : DomainEventBase
{
    public override string EventType => nameof(CommentCreatedEvent);
    
    public long CommentId { get; init; }
    public long PostId { get; init; }
    public string PostTitle { get; init; } = string.Empty;
    public string AuthorId { get; init; } = string.Empty;
    public string PostOwnerId { get; init; } = string.Empty;
    public long? ParentCommentId { get; init; }
    public string? ParentCommentAuthorId { get; init; }
    public string Content { get; init; } = string.Empty;
}
```

### CommentDeletedEvent

```csharp
public record CommentDeletedEvent : DomainEventBase
{
    public override string EventType => nameof(CommentDeletedEvent);
    
    public long CommentId { get; init; }
    public long PostId { get; init; }
    public int DecrementCount { get; init; } = 1;
}
```

### 事件处理器

```csharp
// ZhiCoreCore/Domain/EventHandlers/Comment/CommentCreatedEventHandler.cs
public class CommentCreatedEventHandler : IDomainEventHandler<CommentCreatedEvent>
{
    private readonly IPostStatsRepository _postStatsRepository;
    private readonly INotificationService _notificationService;
    private readonly ILogger<CommentCreatedEventHandler> _logger;
    
    public async Task HandleAsync(CommentCreatedEvent @event, CancellationToken ct = default)
    {
        // 1. 更新文章评论数
        try
        {
            await _postStatsRepository.IncrementCommentCountAsync(@event.PostId, 1);
            _logger.LogDebug("文章评论数已更新: PostId={PostId}", @event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "更新文章评论数失败: PostId={PostId}", @event.PostId);
        }
        
        // 2. 发送评论通知
        try
        {
            if (@event.ParentCommentAuthorId != null && @event.AuthorId != @event.ParentCommentAuthorId)
            {
                // 回复通知
                await _notificationService.NotifyCommentRepliedAsync(
                    @event.ParentCommentAuthorId,
                    @event.PostId,
                    @event.PostTitle,
                    "用户",
                    @event.Content,
                    @event.AuthorId,
                    @event.CommentId,
                    @event.ParentCommentId);
            }
            else if (@event.AuthorId != @event.PostOwnerId)
            {
                // 文章评论通知
                await _notificationService.NotifyPostCommentedAsync(
                    @event.PostOwnerId,
                    @event.PostId,
                    @event.PostTitle,
                    "用户",
                    @event.Content,
                    @event.AuthorId,
                    @event.CommentId);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "发送评论通知失败: CommentId={CommentId}", @event.CommentId);
        }
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `comment:{commentId}` | String | 评论详情 | 10 分钟 |
| `post:{postId}:comments:{page}` | String | 评论列表 | 5 分钟 |
| `comment:stats:{commentId}` | Hash | 评论统计 | 永久 |
| `hot:post:{postId}:comments` | List | 热门文章评论 | 30 分钟 |

### Cached Decorator

```csharp
// ZhiCoreCore/Infrastructure/Caching/CachedCommentService.cs
public class CachedCommentService : ICommentService
{
    private readonly ICommentService _inner;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    
    public async Task<CommentVo> CreateCommentAsync(CreateCommentReq dto, string userId, string? ipAddress = null)
    {
        var result = await _inner.CreateCommentAsync(dto, userId, ipAddress);
        
        // 失效相关缓存
        await InvalidateCommentCacheAsync(dto.PostId);
        
        return result;
    }
    
    public async Task<CommentListVo> GetCommentsAsync(CommentQueryReq query, string? currentUserId = null)
    {
        var cacheKey = $"post:{query.PostId}:comments:{query.Page}:{query.PageSize}:{query.SortBy}";
        
        return await _redisPolicyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                var cached = await _redis.StringGetAsync(cacheKey);
                if (cached.HasValue)
                {
                    return JsonSerializer.Deserialize<CommentListVo>(cached!);
                }
                
                var result = await _inner.GetCommentsAsync(query, currentUserId);
                
                await _redis.StringSetAsync(cacheKey, 
                    JsonSerializer.Serialize(result), 
                    TimeSpan.FromMinutes(5));
                
                return result;
            },
            fallbackAction: async _ =>
            {
                return await _inner.GetCommentsAsync(query, currentUserId);
            },
            operationKey: $"Comment:GetComments:{query.PostId}");
    }
    
    private async Task InvalidateCommentCacheAsync(long postId)
    {
        var pattern = $"post:{postId}:comments:*";
        // 删除匹配的缓存键
        await _redisPolicyProvider.ExecuteSilentAsync(async _ =>
        {
            var server = _redis.Multiplexer.GetServer(_redis.Multiplexer.GetEndPoints().First());
            var keys = server.Keys(pattern: pattern).ToArray();
            if (keys.Any())
            {
                await _redis.KeyDeleteAsync(keys);
            }
        }, operationKey: $"Comment:InvalidateCache:{postId}");
    }
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 10 | 7 |
| 跨上下文依赖 | INotificationService (通过 scope) | 无（通过事件解耦） |
| 业务规则 | 散落在服务中 | 集中在 Domain Service |
| 统计更新 | 直接调用 | 事件处理器异步更新 |
