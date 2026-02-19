# SessionService 详细设计

## 服务概述

SessionService 负责用户会话管理，包括：
- 会话创建（支持多设备登录）
- 会话验证
- 会话撤销（单个/全部）
- 活跃会话列表管理

## 当前实现分析

### 依赖清单（3 依赖）

```csharp
public class SessionService(
    IConnectionMultiplexer redis,
    IOptions<SessionConfig> config,
    ILogger<SessionService> logger
) : ISessionService
```

### 当前架构特点

1. **纯 Redis 存储**：会话数据完全存储在 Redis 中
2. **支持多设备**：同一用户可以在多个设备上同时登录
3. **自动过期**：会话有 TTL，自动过期

### 问题分析

1. **无数据库备份**：Redis 故障时会话数据丢失
2. **缺少审计日志**：没有记录会话创建/撤销历史
3. **缺少领域事件**：会话变更没有通知其他服务

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/ISessionRepository.cs
public interface ISessionRepository
{
    /// <summary>
    /// 获取会话
    /// </summary>
    Task<UserSession?> GetAsync(string userId, string sessionId);
    
    /// <summary>
    /// 验证会话是否有效
    /// </summary>
    Task<bool> ValidateAsync(string userId, string sessionId);
    
    /// <summary>
    /// 创建会话
    /// </summary>
    Task<UserSession> CreateAsync(UserSession session);
    
    /// <summary>
    /// 撤销会话
    /// </summary>
    Task<bool> RevokeAsync(string userId, string sessionId);
    
    /// <summary>
    /// 撤销用户所有会话
    /// </summary>
    Task<int> RevokeAllAsync(string userId);
    
    /// <summary>
    /// 获取用户活跃会话列表
    /// </summary>
    Task<IReadOnlyList<UserSession>> GetActiveSessionsAsync(string userId);
    
    /// <summary>
    /// 获取用户活跃会话数量
    /// </summary>
    Task<int> GetActiveSessionCountAsync(string userId);
    
    /// <summary>
    /// 更新会话最后活跃时间
    /// </summary>
    Task UpdateLastActiveAsync(string userId, string sessionId);
}
```

### Application Service

```csharp
// BlogCore/Application/User/ISessionApplicationService.cs
public interface ISessionApplicationService
{
    Task<UserSession> CreateSessionAsync(string userId, CreateSessionReq req);
    Task<bool> ValidateSessionAsync(string userId, string sessionId);
    Task<bool> RevokeSessionAsync(string userId, string sessionId);
    Task<int> RevokeAllSessionsAsync(string userId);
    Task<IReadOnlyList<SessionVo>> GetActiveSessionsAsync(string userId);
}

// BlogCore/Application/User/SessionApplicationService.cs
public class SessionApplicationService : ISessionApplicationService
{
    private readonly ISessionRepository _sessionRepository;
    private readonly IOptions<SessionConfig> _config;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly ILogger<SessionApplicationService> _logger;
    
    public async Task<UserSession> CreateSessionAsync(string userId, CreateSessionReq req)
    {
        // 1. 检查活跃会话数量限制
        var activeCount = await _sessionRepository.GetActiveSessionCountAsync(userId);
        if (activeCount >= _config.Value.MaxSessionsPerUser)
        {
            _logger.LogWarning("用户 {UserId} 已达到最大会话数限制 {Max}", 
                userId, _config.Value.MaxSessionsPerUser);
            
            // 可选：撤销最旧的会话
            // await RevokeOldestSessionAsync(userId);
        }
        
        // 2. 创建会话
        var session = new UserSession
        {
            Id = Guid.NewGuid().ToString(),
            UserId = userId,
            DeviceInfo = req.DeviceInfo,
            IpAddress = req.IpAddress,
            UserAgent = req.UserAgent,
            CreatedAt = DateTimeOffset.UtcNow,
            ExpiresAt = DateTimeOffset.UtcNow.AddDays(_config.Value.SessionTTLDays),
            LastActiveAt = DateTimeOffset.UtcNow
        };
        
        // 3. 持久化
        await _sessionRepository.CreateAsync(session);
        
        // 4. 发布领域事件
        await _eventDispatcher.DispatchAsync(new SessionCreatedEvent
        {
            UserId = userId,
            SessionId = session.Id,
            DeviceInfo = req.DeviceInfo,
            IpAddress = req.IpAddress
        });
        
        _logger.LogInformation("会话创建成功: UserId={UserId}, SessionId={SessionId}", 
            userId, session.Id);
        
        return session;
    }
    
    public async Task<bool> ValidateSessionAsync(string userId, string sessionId)
    {
        var isValid = await _sessionRepository.ValidateAsync(userId, sessionId);
        
        if (isValid)
        {
            // 更新最后活跃时间（异步，不阻塞）
            _ = _sessionRepository.UpdateLastActiveAsync(userId, sessionId);
        }
        
        return isValid;
    }
    
    public async Task<bool> RevokeSessionAsync(string userId, string sessionId)
    {
        var revoked = await _sessionRepository.RevokeAsync(userId, sessionId);
        
        if (revoked)
        {
            await _eventDispatcher.DispatchAsync(new SessionRevokedEvent
            {
                UserId = userId,
                SessionId = sessionId,
                Reason = "user_logout"
            });
            
            _logger.LogInformation("会话已撤销: UserId={UserId}, SessionId={SessionId}", 
                userId, sessionId);
        }
        
        return revoked;
    }
    
    public async Task<int> RevokeAllSessionsAsync(string userId)
    {
        var count = await _sessionRepository.RevokeAllAsync(userId);
        
        if (count > 0)
        {
            await _eventDispatcher.DispatchAsync(new AllSessionsRevokedEvent
            {
                UserId = userId,
                RevokedCount = count,
                Reason = "user_logout_all"
            });
            
            _logger.LogInformation("已撤销用户所有会话: UserId={UserId}, Count={Count}", 
                userId, count);
        }
        
        return count;
    }
}
```

## 领域事件

### SessionCreatedEvent

```csharp
public record SessionCreatedEvent : DomainEventBase
{
    public override string EventType => nameof(SessionCreatedEvent);
    
    public string UserId { get; init; } = string.Empty;
    public string SessionId { get; init; } = string.Empty;
    public string? DeviceInfo { get; init; }
    public string? IpAddress { get; init; }
}
```

### SessionRevokedEvent

```csharp
public record SessionRevokedEvent : DomainEventBase
{
    public override string EventType => nameof(SessionRevokedEvent);
    
    public string UserId { get; init; } = string.Empty;
    public string SessionId { get; init; } = string.Empty;
    public string Reason { get; init; } = string.Empty;
}
```

## Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `session:{userId}:{sessionId}` | String | 会话详情 JSON | 7 天 |
| `user:{userId}:sessions` | Set | 用户活跃会话 ID 列表 | 7 天 |

### 数据结构示例

```json
// session:{userId}:{sessionId}
{
  "id": "abc123",
  "userId": "user001",
  "deviceInfo": "Chrome on Windows",
  "ipAddress": "192.168.1.1",
  "userAgent": "Mozilla/5.0...",
  "createdAt": "2024-01-01T00:00:00Z",
  "expiresAt": "2024-01-08T00:00:00Z",
  "lastActiveAt": "2024-01-01T12:00:00Z"
}
```

## 缓存策略

### Cached Decorator

```csharp
// 已在 CachedSessionService 中实现
public class CachedSessionService : CacheDecoratorBase, ISessionService
{
    public async Task<bool> ValidateSessionAsync(string userId, string sessionId)
    {
        return await _policyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var sessionKey = $"session:{userId}:{sessionId}";
                return await _redis.KeyExistsAsync(sessionKey);
            },
            defaultValue: false,  // Redis 故障时返回 false（fail-safe）
            operationKey: $"ValidateSession:{userId}:{sessionId}");
    }
    
    public async Task CreateSessionAsync(string userId, string sessionId, UserSessionInfo sessionInfo)
    {
        await _policyProvider.ExecuteSilentAsync(
            async _ =>
            {
                var sessionKey = $"session:{userId}:{sessionId}";
                var activeSessionsKey = $"user:{userId}:sessions";
                
                var json = JsonSerializer.Serialize(sessionInfo);
                var ttl = TimeSpan.FromDays(_config.SessionTTLDays);
                
                var transaction = _redis.CreateTransaction();
                _ = transaction.StringSetAsync(sessionKey, json, ttl);
                _ = transaction.SetAddAsync(activeSessionsKey, sessionId);
                _ = transaction.KeyExpireAsync(activeSessionsKey, ttl);
                
                await transaction.ExecuteAsync();
            },
            operationKey: $"CreateSession:{userId}:{sessionId}");
    }
}
```

## 降级策略

### Redis 故障降级

```csharp
public async Task<bool> ValidateSessionAsync(string userId, string sessionId)
{
    return await _policyProvider.ExecuteSilentAsync(
        async _ =>
        {
            // 主逻辑：Redis 验证
            var sessionKey = $"session:{userId}:{sessionId}";
            return await _redis.KeyExistsAsync(sessionKey);
        },
        defaultValue: false,  // 降级：返回 false，强制重新登录
        operationKey: $"ValidateSession:{userId}:{sessionId}");
}
```

**降级策略说明**：
- Redis 故障时，会话验证返回 `false`
- 用户需要重新登录
- 这是 fail-safe 策略，确保安全性

## 配置

```json
{
  "SessionConfig": {
    "SessionTTLDays": 7,
    "MaxSessionsPerUser": 10,
    "RefreshThresholdHours": 24
  }
}
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 3 | 4 |
| 领域事件 | 无 | SessionCreatedEvent, SessionRevokedEvent |
| 审计日志 | 无 | 通过事件记录 |
| 降级策略 | 无 | fail-safe |
