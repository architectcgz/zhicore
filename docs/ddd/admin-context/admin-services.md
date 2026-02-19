# Admin Services 详细设计

## AdminPostService

### 服务概述

AdminPostService 负责文章的管理操作，包括下架、恢复、批量操作等。

### Application Service

```csharp
// BlogCore/Application/Admin/IAdminPostApplicationService.cs
public interface IAdminPostApplicationService
{
    Task TakeDownPostAsync(string adminId, long postId, TakeDownReq req);
    Task RestorePostAsync(string adminId, long postId, string reason);
    Task BatchTakeDownPostsAsync(string adminId, IEnumerable<long> postIds, TakeDownReq req);
    Task<PaginatedResult<AdminPostVo>> GetPendingReviewPostsAsync(int page, int pageSize);
    Task<PaginatedResult<AdminPostVo>> GetTakenDownPostsAsync(int page, int pageSize);
}

// BlogCore/Application/Admin/AdminPostApplicationService.cs
public class AdminPostApplicationService : IAdminPostApplicationService
{
    private readonly IPostRepository _postRepository;
    private readonly IAdminAuditLogRepository _auditLogRepository;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<AdminPostApplicationService> _logger;
    
    public async Task TakeDownPostAsync(string adminId, long postId, TakeDownReq req)
    {
        // 1. 获取文章
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null)
            throw new BusinessException(BusinessError.PostNotFound);
        
        if (post.Status == PostStatus.TakenDown)
        {
            _logger.LogWarning("文章已下架: PostId={PostId}", postId);
            return;
        }
        
        // 2. 更新状态
        var originalStatus = post.Status;
        post.Status = PostStatus.TakenDown;
        post.UpdateTime = DateTimeOffset.UtcNow;
        
        await _postRepository.UpdateAsync(post);
        
        // 3. 记录审计日志
        await _auditLogRepository.AddAsync(new AdminAuditLog
        {
            AdminId = adminId,
            Action = "TakeDownPost",
            TargetType = "Post",
            TargetId = postId.ToString(),
            Reason = req.Reason,
            Details = JsonSerializer.Serialize(new
            {
                OriginalStatus = originalStatus.ToString(),
                ReasonCode = req.ReasonCode
            }),
            CreatedAt = DateTimeOffset.UtcNow
        });
        
        // 4. 发布领域事件
        await _eventDispatcher.DispatchAsync(new PostTakenDownEvent
        {
            PostId = postId,
            AuthorId = post.OwnerId,
            Title = post.Title,
            AdminId = adminId,
            Reason = req.Reason,
            ReasonCode = req.ReasonCode
        });
        
        _logger.LogInformation("文章已下架: PostId={PostId}, AdminId={AdminId}, Reason={Reason}",
            postId, adminId, req.Reason);
    }
    
    public async Task RestorePostAsync(string adminId, long postId, string reason)
    {
        var post = await _postRepository.GetByIdAsync(postId);
        if (post == null)
            throw new BusinessException(BusinessError.PostNotFound);
        
        if (post.Status != PostStatus.TakenDown)
        {
            _logger.LogWarning("文章未处于下架状态: PostId={PostId}", postId);
            return;
        }
        
        // 恢复为待审核状态
        post.Status = PostStatus.PendingReview;
        post.UpdateTime = DateTimeOffset.UtcNow;
        
        await _postRepository.UpdateAsync(post);
        
        // 记录审计日志
        await _auditLogRepository.AddAsync(new AdminAuditLog
        {
            AdminId = adminId,
            Action = "RestorePost",
            TargetType = "Post",
            TargetId = postId.ToString(),
            Reason = reason,
            CreatedAt = DateTimeOffset.UtcNow
        });
        
        // 发布领域事件
        await _eventDispatcher.DispatchAsync(new PostRestoredEvent
        {
            PostId = postId,
            AuthorId = post.OwnerId,
            AdminId = adminId
        });
        
        _logger.LogInformation("文章已恢复: PostId={PostId}, AdminId={AdminId}", postId, adminId);
    }
}
```

## AdminUserService

### 服务概述

AdminUserService 负责用户的管理操作，包括封禁、解封、角色管理等。

### Application Service

```csharp
// BlogCore/Application/Admin/IAdminUserApplicationService.cs
public interface IAdminUserApplicationService
{
    Task LockUserAsync(string adminId, string userId, LockUserReq req);
    Task UnlockUserAsync(string adminId, string userId, string reason);
    Task<PaginatedResult<AdminUserVo>> GetLockedUsersAsync(int page, int pageSize);
    Task AssignRoleAsync(string adminId, string userId, string role);
    Task RemoveRoleAsync(string adminId, string userId, string role);
}

// BlogCore/Application/Admin/AdminUserApplicationService.cs
public class AdminUserApplicationService : IAdminUserApplicationService
{
    private readonly IUserRepository _userRepository;
    private readonly ISessionRepository _sessionRepository;
    private readonly IAdminAuditLogRepository _auditLogRepository;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<AdminUserApplicationService> _logger;
    
    public async Task LockUserAsync(string adminId, string userId, LockUserReq req)
    {
        // 1. 获取用户
        var user = await _userRepository.GetByIdAsync(userId);
        if (user == null)
            throw new BusinessException(BusinessError.UserNotFound);
        
        if (user.Status == UserStatus.Locked)
        {
            _logger.LogWarning("用户已封禁: UserId={UserId}", userId);
            return;
        }
        
        // 2. 更新状态
        user.Status = UserStatus.Locked;
        user.LockReason = req.Reason;
        user.LockExpireAt = req.IsPermanent ? null : DateTimeOffset.UtcNow.AddDays(req.LockDays);
        user.UpdateTime = DateTimeOffset.UtcNow;
        
        await _userRepository.UpdateAsync(user);
        
        // 3. 撤销所有会话
        var revokedCount = await _sessionRepository.RevokeAllAsync(userId);
        
        // 4. 记录审计日志
        await _auditLogRepository.AddAsync(new AdminAuditLog
        {
            AdminId = adminId,
            Action = "LockUser",
            TargetType = "User",
            TargetId = userId,
            Reason = req.Reason,
            Details = JsonSerializer.Serialize(new
            {
                IsPermanent = req.IsPermanent,
                LockDays = req.LockDays,
                RevokedSessions = revokedCount
            }),
            CreatedAt = DateTimeOffset.UtcNow
        });
        
        // 5. 发布领域事件
        await _eventDispatcher.DispatchAsync(new UserLockedEvent
        {
            UserId = userId,
            AdminId = adminId,
            Reason = req.Reason,
            IsPermanent = req.IsPermanent,
            LockExpireAt = user.LockExpireAt
        });
        
        _logger.LogInformation("用户已封禁: UserId={UserId}, AdminId={AdminId}, IsPermanent={IsPermanent}",
            userId, adminId, req.IsPermanent);
    }
    
    public async Task UnlockUserAsync(string adminId, string userId, string reason)
    {
        var user = await _userRepository.GetByIdAsync(userId);
        if (user == null)
            throw new BusinessException(BusinessError.UserNotFound);
        
        if (user.Status != UserStatus.Locked)
        {
            _logger.LogWarning("用户未处于封禁状态: UserId={UserId}", userId);
            return;
        }
        
        user.Status = UserStatus.Active;
        user.LockReason = null;
        user.LockExpireAt = null;
        user.UpdateTime = DateTimeOffset.UtcNow;
        
        await _userRepository.UpdateAsync(user);
        
        // 记录审计日志
        await _auditLogRepository.AddAsync(new AdminAuditLog
        {
            AdminId = adminId,
            Action = "UnlockUser",
            TargetType = "User",
            TargetId = userId,
            Reason = reason,
            CreatedAt = DateTimeOffset.UtcNow
        });
        
        // 发布领域事件
        await _eventDispatcher.DispatchAsync(new UserUnlockedEvent
        {
            UserId = userId,
            AdminId = adminId,
            Reason = reason
        });
        
        _logger.LogInformation("用户已解封: UserId={UserId}, AdminId={AdminId}", userId, adminId);
    }
}
```

## 领域事件

### PostTakenDownEvent

```csharp
public record PostTakenDownEvent : DomainEventBase
{
    public override string EventType => nameof(PostTakenDownEvent);
    
    public long PostId { get; init; }
    public string AuthorId { get; init; } = string.Empty;
    public string Title { get; init; } = string.Empty;
    public string AdminId { get; init; } = string.Empty;
    public string Reason { get; init; } = string.Empty;
    public string ReasonCode { get; init; } = string.Empty;
}
```

### UserLockedEvent

```csharp
public record UserLockedEvent : DomainEventBase
{
    public override string EventType => nameof(UserLockedEvent);
    
    public string UserId { get; init; } = string.Empty;
    public string AdminId { get; init; } = string.Empty;
    public string Reason { get; init; } = string.Empty;
    public bool IsPermanent { get; init; }
    public DateTimeOffset? LockExpireAt { get; init; }
}
```

### 事件处理器

```csharp
// BlogCore/Domain/EventHandlers/Admin/PostTakenDownEventHandler.cs
public class PostTakenDownEventHandler : IDomainEventHandler<PostTakenDownEvent>
{
    private readonly ISearchIndexService _searchIndexService;
    private readonly INotificationApplicationService _notificationService;
    private readonly ILogger<PostTakenDownEventHandler> _logger;
    
    public async Task HandleAsync(PostTakenDownEvent @event, CancellationToken ct = default)
    {
        // 1. 删除搜索索引
        try
        {
            await _searchIndexService.DeletePostIndexAsync(@event.PostId);
            _logger.LogDebug("文章索引已删除: PostId={PostId}", @event.PostId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "删除文章索引失败: PostId={PostId}", @event.PostId);
        }
        
        // 2. 通知作者
        try
        {
            await _notificationService.CreateNotificationAsync(new CreateNotificationReq
            {
                UserId = @event.AuthorId,
                Type = NotificationType.SystemNotice,
                Title = "您的文章已被下架",
                Content = $"您的文章《{@event.Title}》因{@event.Reason}已被下架，如有疑问请联系管理员。"
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "发送下架通知失败: PostId={PostId}", @event.PostId);
        }
    }
}

// BlogCore/Domain/EventHandlers/Admin/UserLockedEventHandler.cs
public class UserLockedEventHandler : IDomainEventHandler<UserLockedEvent>
{
    private readonly INotificationApplicationService _notificationService;
    private readonly ILogger<UserLockedEventHandler> _logger;
    
    public async Task HandleAsync(UserLockedEvent @event, CancellationToken ct = default)
    {
        // 通知用户（通过邮件，因为用户已无法登录）
        try
        {
            var content = @event.IsPermanent
                ? $"您的账号因{@event.Reason}已被永久封禁。"
                : $"您的账号因{@event.Reason}已被封禁至{@event.LockExpireAt:yyyy-MM-dd HH:mm}。";
            
            // 这里可以发送邮件通知
            _logger.LogInformation("用户封禁通知: UserId={UserId}, Content={Content}", 
                @event.UserId, content);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "发送封禁通知失败: UserId={UserId}", @event.UserId);
        }
    }
}
```

## 权限控制

管理操作需要特定角色：

| 操作 | 所需角色 |
|------|---------|
| 下架文章 | Moderator, Admin |
| 恢复文章 | Admin |
| 封禁用户 | Admin |
| 解封用户 | Admin |
| 处理举报 | Moderator, Admin |
| 查看审计日志 | Admin |

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 5 | 5 |
| 审计日志 | 部分实现 | 完整实现 |
| 事件驱动 | 无 | 支持 |
| 通知机制 | 手动调用 | 事件处理器 |
