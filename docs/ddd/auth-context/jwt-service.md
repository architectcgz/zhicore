# JwtService 详细设计

## 服务概述

JwtService 负责 JWT 令牌的生成和解析，包括：
- Access Token 生成（短期有效）
- Refresh Token 生成（JWT 格式，包含 sessionId）
- Token 解析和验证
- 从 Token 中提取信息（jti、sessionId、过期时间）

## 当前实现分析

### 依赖清单（2 个依赖）

```csharp
public class JwtService(
    IOptions<JwtConfig> jwtConfig, 
    ILogger<JwtService> logger) : IJwtService
```

### 当前架构特点

1. **双 Token 机制**：Access Token（短期）+ Refresh Token（长期）
2. **JWT 格式 Refresh Token**：包含 userId 和 sessionId，支持多设备登录
3. **唯一标识**：每个 Token 都有唯一的 jti（JWT ID）
4. **会话绑定**：Token 与 sessionId 绑定，支持单设备登出

### Token 结构

#### Access Token Claims

| Claim | 说明 |
|-------|------|
| sub | 用户 ID |
| email | 用户邮箱 |
| jti | Token 唯一标识 |
| sid | 会话 ID |
| role | 用户角色（可多个） |

#### Refresh Token Claims

| Claim | 说明 |
|-------|------|
| sub | 用户 ID |
| jti | Token 唯一标识 |
| sid | 会话 ID |
| token_type | "refresh"（标识为刷新令牌） |

## DDD 重构设计

### Domain Service

JwtService 本身就是一个纯粹的领域服务，不依赖外部基础设施（除了配置），重构主要是接口规范化：

```csharp
// BlogCore/Domain/Services/IJwtDomainService.cs
public interface IJwtDomainService
{
    /// <summary>
    /// 生成 Access Token
    /// </summary>
    /// <param name="user">用户信息</param>
    /// <param name="roles">用户角色列表</param>
    /// <param name="sessionId">会话 ID</param>
    /// <returns>JWT Token 字符串</returns>
    string GenerateAccessToken(AppUser user, IList<string> roles, string sessionId);
    
    /// <summary>
    /// 生成 Refresh Token（JWT 格式）
    /// </summary>
    /// <param name="userId">用户 ID</param>
    /// <param name="sessionId">会话 ID</param>
    /// <returns>Refresh Token 信息</returns>
    RefreshToken GenerateRefreshToken(string userId, string sessionId);
    
    /// <summary>
    /// 从过期的 Token 中获取 Principal
    /// </summary>
    ClaimsPrincipal? GetPrincipalFromExpiredToken(string token);
    
    /// <summary>
    /// 从 Token 中提取 jti
    /// </summary>
    string? GetJtiFromToken(string token);
    
    /// <summary>
    /// 从 Token 中提取 sessionId
    /// </summary>
    string? GetSessionIdFromToken(string token);
    
    /// <summary>
    /// 获取 Token 的过期时间
    /// </summary>
    DateTimeOffset? GetTokenExpiration(string token);
}

// BlogCore/Domain/Services/JwtDomainService.cs
public class JwtDomainService : IJwtDomainService
{
    private readonly JwtConfig _config;
    private readonly ILogger<JwtDomainService> _logger;
    
    public string GenerateAccessToken(AppUser user, IList<string> roles, string sessionId)
    {
        var jti = Guid.NewGuid().ToString("N");
        var claims = new List<Claim>
        {
            new(JwtRegisteredClaimNames.Sub, user.Id),
            new(JwtRegisteredClaimNames.Email, user.Email ?? ""),
            new(JwtRegisteredClaimNames.Jti, jti),
            new(ClaimTypes.NameIdentifier, user.Id),
            new(JwtClaimTypes.SessionId, sessionId)
        };
        
        // 添加角色声明
        claims.AddRange(roles.Select(role => new Claim(JwtClaimTypes.Role, role)));
        
        var key = CreateSigningKey();
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);
        var expires = DateTimeOffset.UtcNow.AddMinutes(_config.AccessTokenExpiryMinutes);
        
        var token = new JwtSecurityToken(
            issuer: _config.Issuer,
            audience: _config.Audience,
            claims: claims,
            expires: expires.UtcDateTime,
            signingCredentials: creds
        );
        
        _logger.LogDebug("生成 Access Token: jti={Jti}, userId={UserId}, sessionId={SessionId}", 
            jti, user.Id, sessionId);
        
        return new JwtSecurityTokenHandler().WriteToken(token);
    }
    
    public RefreshToken GenerateRefreshToken(string userId, string sessionId)
    {
        var jti = Guid.NewGuid().ToString("N");
        var claims = new List<Claim>
        {
            new(JwtRegisteredClaimNames.Sub, userId),
            new(JwtRegisteredClaimNames.Jti, jti),
            new(ClaimTypes.NameIdentifier, userId),
            new(JwtClaimTypes.SessionId, sessionId),
            new(JwtClaimTypes.TokenType, JwtClaimTypes.TokenTypeValues.Refresh)
        };
        
        var key = CreateSigningKey();
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);
        var expires = DateTimeOffset.UtcNow.AddMinutes(_config.RefreshTokenExpiryMinutes);
        
        var token = new JwtSecurityToken(
            issuer: _config.Issuer,
            audience: _config.Audience,
            claims: claims,
            expires: expires.UtcDateTime,
            signingCredentials: creds
        );
        
        var tokenString = new JwtSecurityTokenHandler().WriteToken(token);
        
        _logger.LogDebug("生成 Refresh Token: jti={Jti}, userId={UserId}, sessionId={SessionId}", 
            jti, userId, sessionId);
        
        return new RefreshToken
        {
            Token = tokenString,
            Expires = expires,
            Created = DateTimeOffset.UtcNow
        };
    }
    
    private SymmetricSecurityKey CreateSigningKey()
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_config.Key));
        key.KeyId = Convert.ToBase64String(Encoding.UTF8.GetBytes(_config.KeyId));
        return key;
    }
}
```

## 配置

```json
{
  "JwtConfig": {
    "Key": "your-secret-key-at-least-32-characters-long",
    "KeyId": "blog-api-key-v1",
    "Issuer": "blog-api",
    "Audience": "blog-frontend",
    "AccessTokenExpiryMinutes": 30,
    "RefreshTokenExpiryMinutes": 10080
  }
}
```

## Token 黑名单机制

当用户登出或刷新 Token 时，旧的 Access Token 需要加入黑名单：

```csharp
// 在 SessionService 中实现
public async Task BlacklistTokenAsync(string jti, TimeSpan remainingTime)
{
    if (remainingTime <= TimeSpan.Zero)
        return;
    
    var key = $"token:blacklist:{jti}";
    await _redis.StringSetAsync(key, "1", remainingTime);
    
    _logger.LogDebug("Token 已加入黑名单: jti={Jti}, 剩余有效期={RemainingTime}", jti, remainingTime);
}

public async Task<bool> IsTokenBlacklistedAsync(string jti)
{
    var key = $"token:blacklist:{jti}";
    return await _redis.KeyExistsAsync(key);
}
```

## Token 验证中间件

```csharp
// 在 JWT 验证中间件中检查黑名单
public class JwtBlacklistMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ISessionService _sessionService;
    
    public async Task InvokeAsync(HttpContext context)
    {
        var token = context.Request.Headers["Authorization"].ToString().Replace("Bearer ", "");
        
        if (!string.IsNullOrEmpty(token))
        {
            var jti = _jwtService.GetJtiFromToken(token);
            if (!string.IsNullOrEmpty(jti))
            {
                var isBlacklisted = await _sessionService.IsTokenBlacklistedAsync(jti);
                if (isBlacklisted)
                {
                    context.Response.StatusCode = 401;
                    await context.Response.WriteAsJsonAsync(new { error = "Token 已失效" });
                    return;
                }
            }
        }
        
        await _next(context);
    }
}
```

## 安全考虑

1. **密钥管理**：生产环境使用环境变量或密钥管理服务
2. **Token 有效期**：Access Token 短期（30分钟），Refresh Token 长期（7天）
3. **黑名单机制**：登出时将未过期的 Token 加入黑名单
4. **会话绑定**：Token 与 sessionId 绑定，支持精确的会话管理
5. **HTTPS**：生产环境必须使用 HTTPS 传输 Token

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 2 | 2 |
| 职责 | 清晰 | 清晰 |
| 可测试性 | 高 | 高 |
| 备注 | 已是纯领域服务 | 接口规范化 |
