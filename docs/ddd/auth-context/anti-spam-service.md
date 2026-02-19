# AntiSpamService 详细设计

## 服务概述

AntiSpamService 负责防刷机制的实现，保护系统免受恶意操作：
- 操作频率限制（每分钟/每小时/每天）
- 冷却时间控制（取消操作后的等待期）
- 同一目标操作限制
- 陌生人消息限制
- 操作历史记录

## 当前实现分析

### 依赖清单（6 个依赖）

```csharp
public class AntiSpamService(
    AppDbContext dbContext,
    IOptions<AntiSpamConfig> antiSpamConfig,
    ILogger<AntiSpamService> logger,
    IEventPublisher eventPublisher,
    IConnectionMultiplexer redis,
    IRedisPolicyProvider redisPolicyProvider) : IAntiSpamService
```

### 支持的操作类型

| 操作类型 | 限制规则 | 冷却时间 |
|---------|---------|---------|
| Follow | 每小时/每天限制，取消关注后冷却 | 可配置（分钟） |
| Comment | 每分钟/每小时/每天限制 | 可配置（秒） |
| Like | 每分钟/每小时限制，取消点赞后冷却 | 可配置（秒） |
| Favorite | 每分钟/每小时限制，取消收藏后冷却 | 可配置（秒） |
| Message | 每分钟/每小时/每天限制，陌生人限制 | 可配置（秒） |
| Feedback | 每天限制 | 无 |
| Report | 每天限制，同一目标限制 | 无 |

### 当前架构特点

1. **Redis + 数据库双重存储**：Redis 用于快速检查，数据库用于持久化历史
2. **Polly 熔断保护**：Redis 不可用时降级到数据库查询
3. **异步记录**：操作历史通过 RabbitMQ 异步写入
4. **可配置规则**：所有限制参数通过配置文件管理

### 问题分析

1. **方法过长**：CheckActionAsync 包含大量 switch-case
2. **重复代码**：各操作类型的检查逻辑相似
3. **缺少领域事件**：被限制时未发布事件

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IUserActionHistoryRepository.cs
public interface IUserActionHistoryRepository
{
    /// <summary>
    /// 获取指定时间范围内的操作次数
    /// </summary>
    Task<int> GetActionCountAsync(
        string userId, 
        AntiSpamActionType actionType, 
        DateTimeOffset since,
        string? targetId = null);
    
    /// <summary>
    /// 获取最后一次操作时间
    /// </summary>
    Task<DateTimeOffset?> GetLastActionTimeAsync(
        string userId, 
        AntiSpamActionType actionType, 
        string? targetId = null,
        UserAction? action = null);
    
    /// <summary>
    /// 添加操作记录
    /// </summary>
    Task AddAsync(UserActionHistory history);
    
    /// <summary>
    /// 清理过期记录
    /// </summary>
    Task CleanupOldRecordsAsync(int olderThanDays);
}
```

### Domain Service

```csharp
// BlogCore/Domain/Services/IAntiSpamDomainService.cs
public interface IAntiSpamDomainService
{
    /// <summary>
    /// 检查冷却时间
    /// </summary>
    Task<AntiSpamCheckResult> CheckCooldownAsync(
        string userId, 
        AntiSpamActionType actionType, 
        string? targetId,
        int cooldownSeconds);
    
    /// <summary>
    /// 检查频率限制
    /// </summary>
    Task<AntiSpamCheckResult> CheckRateLimitAsync(
        string userId, 
        AntiSpamActionType actionType, 
        TimeSpan window,
        int maxCount,
        string? targetId = null);
}

// BlogCore/Domain/Services/AntiSpamDomainService.cs
public class AntiSpamDomainService : IAntiSpamDomainService
{
    private readonly IUserActionHistoryRepository _historyRepository;
    private readonly IDatabase _redis;
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    
    public async Task<AntiSpamCheckResult> CheckCooldownAsync(
        string userId, 
        AntiSpamActionType actionType, 
        string? targetId,
        int cooldownSeconds)
    {
        if (cooldownSeconds <= 0 || string.IsNullOrEmpty(targetId))
            return new AntiSpamCheckResult { IsBlocked = false };
        
        var now = DateTimeOffset.UtcNow;
        
        // 优先从 Redis 检查
        var lastActionTime = await GetLastActionTimeFromCacheAsync(userId, actionType, targetId);
        
        if (!lastActionTime.HasValue)
        {
            // Redis 未命中，从数据库查询
            lastActionTime = await _historyRepository.GetLastActionTimeAsync(
                userId, actionType, targetId, UserAction.Delete);
        }
        
        if (lastActionTime.HasValue)
        {
            var cooldownEnd = lastActionTime.Value.AddSeconds(cooldownSeconds);
            if (now < cooldownEnd)
            {
                var remainingSeconds = (int)(cooldownEnd - now).TotalSeconds;
                return new AntiSpamCheckResult
                {
                    IsBlocked = true,
                    Reason = $"操作过于频繁，请等待 {remainingSeconds} 秒后再试",
                    CooldownSeconds = remainingSeconds,
                    LimitType = AntiSpamLimitType.Cooldown
                };
            }
        }
        
        return new AntiSpamCheckResult { IsBlocked = false };
    }
    
    public async Task<AntiSpamCheckResult> CheckRateLimitAsync(
        string userId, 
        AntiSpamActionType actionType, 
        TimeSpan window,
        int maxCount,
        string? targetId = null)
    {
        if (maxCount <= 0)
            return new AntiSpamCheckResult { IsBlocked = false };
        
        var since = DateTimeOffset.UtcNow.Subtract(window);
        
        // 优先从 Redis 检查计数器
        var count = await GetCountFromCacheAsync(userId, actionType, window);
        
        if (!count.HasValue)
        {
            // Redis 未命中，从数据库查询
            count = await _historyRepository.GetActionCountAsync(userId, actionType, since, targetId);
        }
        
        if (count >= maxCount)
        {
            return new AntiSpamCheckResult
            {
                IsBlocked = true,
                Reason = GetRateLimitMessage(actionType, window, maxCount),
                LimitType = AntiSpamLimitType.RateLimit
            };
        }
        
        return new AntiSpamCheckResult { IsBlocked = false };
    }
    
    private string GetRateLimitMessage(AntiSpamActionType actionType, TimeSpan window, int maxCount)
    {
        var actionName = actionType switch
        {
            AntiSpamActionType.Follow => "关注操作",
            AntiSpamActionType.Comment => "评论",
            AntiSpamActionType.Like => "点赞",
            AntiSpamActionType.Favorite => "收藏",
            AntiSpamActionType.Message => "消息发送",
            _ => "操作"
        };
        
        var windowName = window.TotalHours >= 24 ? "今日" : 
                        window.TotalHours >= 1 ? "每小时" : "每分钟";
        
        return $"{actionName}过于频繁，{windowName}最多 {maxCount} 次";
    }
}
```

### Application Service

```csharp
// BlogCore/Application/Auth/IAntiSpamApplicationService.cs
public interface IAntiSpamApplicationService
{
    Task<AntiSpamCheckResult> CheckActionAsync(
        AntiSpamActionType actionType, 
        string userId, 
        string? targetId = null, 
        string? ipAddress = null);
    
    Task RecordActionAsync(
        AntiSpamActionType actionType, 
        string userId, 
        string? targetId = null, 
        string? ipAddress = null,
        UserAction action = UserAction.Create);
    
    Task CleanupOldHistoryAsync(int olderThanDays = 30);
}

// BlogCore/Application/Auth/AntiSpamApplicationService.cs
public class AntiSpamApplicationService : IAntiSpamApplicationService
{
    private readonly IAntiSpamDomainService _domainService;
    private readonly IUserActionHistoryRepository _historyRepository;
    private readonly IEventPublisher _eventPublisher;
    private readonly AntiSpamConfig _config;
    private readonly ILogger<AntiSpamApplicationService> _logger;
    
    public async Task<AntiSpamCheckResult> CheckActionAsync(
        AntiSpamActionType actionType, 
        string userId, 
        string? targetId = null, 
        string? ipAddress = null)
    {
        // 如果防刷机制被禁用，直接允许
        if (!_config.EnableAntiSpam)
            return new AntiSpamCheckResult { IsBlocked = false };
        
        // 根据操作类型获取配置并检查
        var result = actionType switch
        {
            AntiSpamActionType.Follow => await CheckFollowAsync(userId, targetId),
            AntiSpamActionType.Comment => await CheckCommentAsync(userId, targetId),
            AntiSpamActionType.Like => await CheckLikeAsync(userId, targetId),
            AntiSpamActionType.Favorite => await CheckFavoriteAsync(userId, targetId),
            AntiSpamActionType.Message => await CheckMessageAsync(userId, targetId),
            _ => new AntiSpamCheckResult { IsBlocked = false }
        };
        
        if (result.IsBlocked)
        {
            _logger.LogWarning("用户 {UserId} 的 {ActionType} 操作被限制: {Reason}", 
                userId, actionType, result.Reason);
        }
        
        return result;
    }
    
    private async Task<AntiSpamCheckResult> CheckFollowAsync(string userId, string? targetId)
    {
        var config = _config.Follow;
        
        // 1. 检查冷却时间
        var cooldownResult = await _domainService.CheckCooldownAsync(
            userId, AntiSpamActionType.Follow, targetId, 
            config.FollowCooldownMinutes * 60);
        if (cooldownResult.IsBlocked) return cooldownResult;
        
        // 2. 检查每小时限制
        var hourlyResult = await _domainService.CheckRateLimitAsync(
            userId, AntiSpamActionType.Follow, TimeSpan.FromHours(1),
            config.MaxFollowActionsPerHour);
        if (hourlyResult.IsBlocked) return hourlyResult;
        
        // 3. 检查每天限制
        var dailyResult = await _domainService.CheckRateLimitAsync(
            userId, AntiSpamActionType.Follow, TimeSpan.FromDays(1),
            config.MaxFollowActionsPerDay);
        if (dailyResult.IsBlocked) return dailyResult;
        
        // 4. 检查同一目标限制
        var sameTargetResult = await _domainService.CheckRateLimitAsync(
            userId, AntiSpamActionType.Follow, TimeSpan.FromHours(1),
            config.MaxSameUserActionsPerHour, targetId);
        if (sameTargetResult.IsBlocked) return sameTargetResult;
        
        return new AntiSpamCheckResult { IsBlocked = false };
    }
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `antispam:like:cooldown:{userId}:{targetId}` | String | 点赞冷却时间 | 冷却时间 + 10秒 |
| `antispam:like:count:{userId}:minute` | String | 每分钟点赞计数 | 1 分钟 |
| `antispam:like:count:{userId}:hour` | String | 每小时点赞计数 | 1 小时 |
| `antispam:like:target:{userId}:{targetId}` | String | 同一目标操作计数 | 1 小时 |
| `antispam:message:cooldown:{userId}` | String | 消息冷却时间 | 冷却时间 + 10秒 |
| `antispam:stranger:{userId}:{targetId}` | String | 陌生人关系缓存 | 1 小时 |

### 缓存更新策略

```csharp
// 记录操作时更新 Redis 计数器
public async Task RecordActionAsync(...)
{
    // 异步发布到 MQ（持久化到数据库）
    _ = Task.Run(async () =>
    {
        await _eventPublisher.PublishAntiSpamActionAsync(message);
    });
    
    // 同步更新 Redis 计数器（用于快速检查）
    await _redisPolicyProvider.ExecuteSilentAsync(async _ =>
    {
        var batch = _redis.CreateBatch();
        
        // 更新分钟计数器
        batch.StringIncrementAsync(minuteKey);
        batch.KeyExpireAsync(minuteKey, TimeSpan.FromMinutes(1));
        
        // 更新小时计数器
        batch.StringIncrementAsync(hourKey);
        batch.KeyExpireAsync(hourKey, TimeSpan.FromHours(1));
        
        batch.Execute();
    });
}
```

## 降级策略

### Redis 不可用时的降级

```csharp
// 使用 Polly 熔断器保护 Redis 操作
var count = await _redisPolicyProvider.ExecuteSilentAsync<int?>(
    async _ =>
    {
        var value = await _redis.StringGetAsync(countKey);
        return value.HasValue ? (int?)int.Parse(value!) : null;
    },
    defaultValue: null,
    operationKey: "AntiSpam:GetCount");

if (!count.HasValue)
{
    // Redis 不可用，降级到数据库查询
    _logger.LogWarning("Redis 不可用，防刷检查降级到数据库");
    count = await _historyRepository.GetActionCountAsync(userId, actionType, since);
}
```

## 配置示例

```json
{
  "AntiSpam": {
    "EnableAntiSpam": true,
    "Follow": {
      "FollowCooldownMinutes": 5,
      "MaxFollowActionsPerHour": 30,
      "MaxFollowActionsPerDay": 100,
      "MaxSameUserActionsPerHour": 3
    },
    "Comment": {
      "CommentCooldownSeconds": 10,
      "MaxCommentsPerMinute": 5,
      "MaxCommentsPerHour": 30,
      "MaxCommentsPerDay": 100,
      "MaxCommentsPerPostPerHour": 10
    },
    "Like": {
      "LikeCooldownSeconds": 3,
      "MaxLikesPerMinute": 30,
      "MaxLikesPerHour": 200,
      "MaxSameTargetActionsPerHour": 5
    },
    "Message": {
      "MessageCooldownSeconds": 2,
      "MaxMessagesPerMinute": 10,
      "MaxMessagesPerHour": 60,
      "MaxMessagesPerDay": 200,
      "EnableStrangerMessageLimit": true,
      "MaxMessagesToStrangerBeforeReply": 3
    }
  }
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 6 | 5 |
| 代码重复 | 高（各操作类型检查逻辑相似） | 低（抽象为通用方法） |
| 可配置性 | 高 | 高 |
| 可测试性 | 中 | 高（Domain Service 可独立测试） |
