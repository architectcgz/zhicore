# UserService 详细设计

## 服务概述

UserService 负责用户基本信息的管理，包括：
- 用户信息查询（单个、批量）
- 用户资料更新
- 用户头像管理
- 用户统计信息

## 当前实现分析

### 依赖清单（2 依赖）

```csharp
public class UserService(
    AppDbContext dbContext,
    IMapper mapper
) : IUserService
```

### 当前架构特点

1. **简单直接**：直接通过 DbContext 查询用户信息
2. **无缓存**：缓存逻辑在 CachedUserService 装饰器中
3. **职责单一**：仅处理用户基本信息 CRUD

### 问题分析

1. **缺少领域事件**：用户资料更新后没有发布事件
2. **跨上下文数据冗余**：其他上下文需要用户信息时直接查询

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IUserRepository.cs
public interface IUserRepository
{
    /// <summary>
    /// 根据ID获取用户
    /// </summary>
    Task<User?> GetByIdAsync(string userId);
    
    /// <summary>
    /// 根据用户名获取用户
    /// </summary>
    Task<User?> GetByUserNameAsync(string userName);
    
    /// <summary>
    /// 根据邮箱获取用户
    /// </summary>
    Task<User?> GetByEmailAsync(string email);
    
    /// <summary>
    /// 批量获取用户
    /// </summary>
    Task<IReadOnlyList<User>> GetByIdsAsync(IEnumerable<string> userIds);
    
    /// <summary>
    /// 检查用户是否存在
    /// </summary>
    Task<bool> ExistsAsync(string userId);
    
    /// <summary>
    /// 检查用户名是否已存在
    /// </summary>
    Task<bool> UserNameExistsAsync(string userName, string? excludeUserId = null);
    
    /// <summary>
    /// 检查邮箱是否已存在
    /// </summary>
    Task<bool> EmailExistsAsync(string email, string? excludeUserId = null);
    
    /// <summary>
    /// 添加用户
    /// </summary>
    Task<User> AddAsync(User user);
    
    /// <summary>
    /// 更新用户
    /// </summary>
    Task UpdateAsync(User user);
    
    /// <summary>
    /// 获取用户基本信息（用于缓存）
    /// </summary>
    Task<UserBasicInfo?> GetBasicInfoAsync(string userId);
    
    /// <summary>
    /// 批量获取用户基本信息
    /// </summary>
    Task<Dictionary<string, UserBasicInfo>> GetBasicInfoBatchAsync(IEnumerable<string> userIds);
    
    /// <summary>
    /// 获取用户统计信息
    /// </summary>
    Task<UserStatsDto?> GetStatsAsync(string userId);
    
    /// <summary>
    /// 更新用户统计
    /// </summary>
    Task UpdateStatsAsync(string userId, Action<UserStats> updateAction);
}
```

### Application Service

```csharp
// BlogCore/Application/User/IUserApplicationService.cs
public interface IUserApplicationService
{
    Task<UserProfileVo?> GetUserProfileAsync(string userId, string? currentUserId = null);
    Task<UserBasicInfo?> GetUserBasicInfoAsync(string userId);
    Task<Dictionary<string, UserBasicInfo>> GetUserBasicInfoBatchAsync(IEnumerable<string> userIds);
    Task UpdateUserProfileAsync(string userId, UpdateUserProfileReq req);
    Task UpdateUserAvatarAsync(string userId, string avatarUrl);
    Task<UserStatsDto?> GetUserStatsAsync(string userId);
}

// BlogCore/Application/User/UserApplicationService.cs
public class UserApplicationService : IUserApplicationService
{
    private readonly IUserRepository _userRepository;
    private readonly IUserFollowRepository _userFollowRepository;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly IMapper _mapper;
    private readonly ILogger<UserApplicationService> _logger;
    
    public async Task<UserProfileVo?> GetUserProfileAsync(string userId, string? currentUserId = null)
    {
        var user = await _userRepository.GetByIdAsync(userId);
        if (user == null) return null;
        
        var profile = _mapper.Map<UserProfileVo>(user);
        
        // 获取统计信息
        var stats = await _userRepository.GetStatsAsync(userId);
        if (stats != null)
        {
            profile.PostCount = stats.PostCount;
            profile.FollowerCount = stats.FollowerCount;
            profile.FollowingCount = stats.FollowingCount;
        }
        
        // 如果有当前用户，获取关注状态
        if (!string.IsNullOrEmpty(currentUserId) && currentUserId != userId)
        {
            profile.IsFollowing = await _userFollowRepository.ExistsAsync(currentUserId, userId);
            profile.IsFollowedBy = await _userFollowRepository.ExistsAsync(userId, currentUserId);
        }
        
        return profile;
    }
    
    public async Task UpdateUserProfileAsync(string userId, UpdateUserProfileReq req)
    {
        var user = await _userRepository.GetByIdAsync(userId);
        if (user == null)
            throw new BusinessException(BusinessError.UserNotFound);
        
        // 检查昵称是否重复
        if (!string.IsNullOrEmpty(req.NickName) && req.NickName != user.NickName)
        {
            var nickNameExists = await _userRepository.UserNameExistsAsync(req.NickName, userId);
            if (nickNameExists)
                throw new BusinessException(BusinessError.NickNameAlreadyExists);
        }
        
        // 更新用户信息
        var oldNickName = user.NickName;
        var oldAvatar = user.AvatarUrl;
        
        user.NickName = req.NickName ?? user.NickName;
        user.Bio = req.Bio ?? user.Bio;
        user.Gender = req.Gender ?? user.Gender;
        user.Birthday = req.Birthday ?? user.Birthday;
        user.UpdateTime = DateTimeOffset.UtcNow;
        
        await _userRepository.UpdateAsync(user);
        
        // 发布领域事件（用于更新其他上下文中的冗余数据）
        if (oldNickName != user.NickName || oldAvatar != user.AvatarUrl)
        {
            await _eventDispatcher.DispatchAsync(new UserProfileUpdatedEvent
            {
                UserId = userId,
                NickName = user.NickName,
                AvatarUrl = user.AvatarUrl,
                OldNickName = oldNickName,
                OldAvatarUrl = oldAvatar
            });
        }
        
        _logger.LogInformation("用户资料更新成功: UserId={UserId}", userId);
    }
}
```

## 领域事件

### UserProfileUpdatedEvent

```csharp
public record UserProfileUpdatedEvent : DomainEventBase
{
    public override string EventType => nameof(UserProfileUpdatedEvent);
    
    public string UserId { get; init; } = string.Empty;
    public string NickName { get; init; } = string.Empty;
    public string? AvatarUrl { get; init; }
    public string? OldNickName { get; init; }
    public string? OldAvatarUrl { get; init; }
}
```

### 事件处理器

```csharp
// BlogCore/Domain/EventHandlers/User/UserProfileUpdatedEventHandler.cs
public class UserProfileUpdatedEventHandler : IDomainEventHandler<UserProfileUpdatedEvent>
{
    private readonly IDatabase _redis;
    private readonly ILogger<UserProfileUpdatedEventHandler> _logger;
    
    public async Task HandleAsync(UserProfileUpdatedEvent @event, CancellationToken ct = default)
    {
        // 1. 失效用户缓存
        try
        {
            var cacheKey = $"user:{@event.UserId}:basic";
            await _redis.KeyDeleteAsync(cacheKey);
            _logger.LogDebug("用户缓存已失效: UserId={UserId}", @event.UserId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "失效用户缓存失败: UserId={UserId}", @event.UserId);
        }
        
        // 2. 其他上下文会订阅此事件更新冗余数据
        // - Post Context: 更新文章作者信息
        // - Comment Context: 更新评论作者信息
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `user:{userId}:basic` | String | 用户基本信息 | 24 小时 |
| `user:{userId}:profile` | String | 用户完整资料 | 10 分钟 |
| `user:{userId}:stats` | Hash | 用户统计 | 永久 |

### Cached Decorator

```csharp
// 已在 CachedUserService 中实现
public class CachedUserService : CacheDecoratorBase, IUserService
{
    public async Task<UserBasicInfo?> GetUserBasicInfoAsync(string userId)
    {
        var cacheKey = $"user:{userId}:basic";
        
        var cached = await GetFromCacheAsync<UserBasicInfo>(cacheKey, $"GetUserBasicInfo:{userId}");
        if (cached != null) return cached;
        
        var result = await _inner.GetUserBasicInfoAsync(userId);
        
        if (result != null)
        {
            await SetToCacheAsync(cacheKey, result, TimeSpan.FromHours(24), $"SetUserBasicInfo:{userId}");
        }
        
        return result;
    }
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 2 | 5 |
| 领域事件 | 无 | UserProfileUpdatedEvent |
| 缓存失效 | 手动 | 事件驱动 |
| 跨上下文同步 | 无 | 通过事件 |
