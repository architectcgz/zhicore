# Auth Context（认证上下文）

## 概述

认证上下文负责用户认证和安全相关功能，包括登录、注册、JWT 管理、防刷机制等。

## 服务清单

| 服务 | 接口 | 当前依赖数 | 主要职责 |
|------|------|-----------|---------|
| AuthService | IAuthService | 8 | 用户认证（登录、注册、密码重置） |
| AntiSpamService | IAntiSpamService | 6 | 防刷机制 |
| JwtService | IJwtService | 3 | JWT 令牌管理 |

## 认证流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户登录请求                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AntiSpamService                             │
│                    检查登录频率限制                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       AuthService                                │
│                    验证用户名密码                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       JwtService                                 │
│                    生成 Access Token + Refresh Token             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SessionService                              │
│                    创建会话记录（支持多设备）                      │
└─────────────────────────────────────────────────────────────────┘
```

## 防刷机制

### 支持的操作类型

| 操作类型 | 限制规则 | 冷却时间 |
|---------|---------|---------|
| Login | 5 次/分钟 | 1 分钟 |
| Register | 3 次/小时 | 1 小时 |
| Like | 60 次/分钟 | 无 |
| Comment | 10 次/分钟 | 30 秒 |
| Follow | 30 次/分钟 | 无 |
| Message | 20 次/分钟 | 无 |

### 实现原理

```csharp
public class AntiSpamService : IAntiSpamService
{
    public async Task<AntiSpamResult> CheckActionAsync(
        AntiSpamActionType actionType,
        string userId,
        string? targetId = null,
        string? ipAddress = null)
    {
        // 1. 检查用户级别限制
        var userKey = $"antispam:{actionType}:user:{userId}";
        var userCount = await _redis.StringIncrementAsync(userKey);
        if (userCount == 1)
        {
            await _redis.KeyExpireAsync(userKey, GetWindowDuration(actionType));
        }
        
        if (userCount > GetUserLimit(actionType))
        {
            return new AntiSpamResult
            {
                IsBlocked = true,
                Reason = "操作过于频繁，请稍后再试",
                CooldownSeconds = GetCooldownSeconds(actionType)
            };
        }
        
        // 2. 检查 IP 级别限制（可选）
        if (!string.IsNullOrEmpty(ipAddress))
        {
            var ipKey = $"antispam:{actionType}:ip:{ipAddress}";
            var ipCount = await _redis.StringIncrementAsync(ipKey);
            // ...
        }
        
        return new AntiSpamResult { IsBlocked = false };
    }
}
```

## JWT 配置

```json
{
  "JwtConfig": {
    "Key": "your-secret-key-at-least-32-characters",
    "Issuer": "blog-api",
    "Audience": "blog-frontend",
    "AccessTokenExpireMinutes": 30,
    "RefreshTokenExpireDays": 7
  }
}
```

## 多设备登录

支持同一用户在多个设备上同时登录：

```csharp
public class SessionService : ISessionService
{
    public async Task<Session> CreateSessionAsync(string userId, string deviceInfo)
    {
        var session = new Session
        {
            Id = Guid.NewGuid().ToString(),
            UserId = userId,
            DeviceInfo = deviceInfo,
            CreatedAt = DateTimeOffset.UtcNow,
            ExpiresAt = DateTimeOffset.UtcNow.AddDays(7)
        };
        
        // 存储到 Redis
        var key = $"session:{session.Id}";
        await _redis.StringSetAsync(key, JsonSerializer.Serialize(session), TimeSpan.FromDays(7));
        
        // 添加到用户会话列表
        await _redis.SetAddAsync($"user:{userId}:sessions", session.Id);
        
        return session;
    }
    
    public async Task<IReadOnlyList<Session>> GetUserSessionsAsync(string userId)
    {
        var sessionIds = await _redis.SetMembersAsync($"user:{userId}:sessions");
        var sessions = new List<Session>();
        
        foreach (var sessionId in sessionIds)
        {
            var sessionData = await _redis.StringGetAsync($"session:{sessionId}");
            if (sessionData.HasValue)
            {
                sessions.Add(JsonSerializer.Deserialize<Session>(sessionData!));
            }
        }
        
        return sessions;
    }
}
```

## Redis Key 设计

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `session:{sessionId}` | String | 会话信息 | 7 天 |
| `user:{userId}:sessions` | Set | 用户会话列表 | 永久 |
| `antispam:{action}:user:{userId}` | String | 用户操作计数 | 1-60 分钟 |
| `antispam:{action}:ip:{ip}` | String | IP 操作计数 | 1-60 分钟 |
| `refresh_token:{tokenId}` | String | Refresh Token | 7 天 |

## 详细文档

- [AuthService 详细设计](./auth-service.md) ✅
- [AntiSpamService 详细设计](./anti-spam-service.md) ✅
- [JwtService 详细设计](./jwt-service.md) ✅
