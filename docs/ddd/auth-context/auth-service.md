# AuthService 详细设计

## 服务概述

AuthService 负责用户认证的完整生命周期管理，包括：
- 用户注册（邮箱验证）
- 用户登录（支持邮箱/昵称登录）
- Token 刷新（JWT + Refresh Token 双令牌机制）
- 用户登出（支持单设备/全设备登出）
- 密码重置和修改（邮箱验证码）
- 验证码发送（注册、找回密码、修改密码）

## API 端点映射

| HTTP 方法 | 路由 | 方法 | 说明 |
|-----------|------|------|------|
| POST | `/api/auth/register` | RegisterAsync | 用户注册 |
| POST | `/api/auth/login` | LoginAsync | 用户登录 |
| POST | `/api/auth/refresh` | RefreshTokenAsync | 刷新 Token |
| POST | `/api/auth/logout` | LogoutAsync | 用户登出 |
| POST | `/api/auth/logout-all` | LogoutAllAsync | 登出所有设备 |
| POST | `/api/auth/send-code` | SendVerificationCodeAsync | 发送验证码 |
| POST | `/api/auth/reset-password` | ResetPasswordAsync | 重置密码 |
| POST | `/api/auth/change-password` | ChangePasswordAsync | 修改密码 |

## 当前实现分析

### 依赖清单（10 个依赖）

```csharp
public class AuthService(
    AppDbContext appDbContext,
    UserManager<AppUser> userManager,
    SignInManager<AppUser> signInManager,
    IJwtService jwtService,
    ISessionService sessionService,
    ILogger<AuthService> logger,
    IEventPublisher eventPublisher,
    ISnowflakeIdService snowflakeIdService,
    IHttpContextAccessor httpContextAccessor,
    IRabbitMqPolicyProvider rabbitMqPolicyProvider) : IAuthService
```

### 当前架构特点

1. **多设备登录支持**：通过 SessionService 管理多个会话
2. **JWT + Refresh Token**：双 Token 机制，支持无感刷新
3. **邮件异步发送**：通过 RabbitMQ 异步发送验证码邮件
4. **Polly 降级**：MQ 不可用时邮件发送会失败

### 问题分析

1. **方法过长**：LoginAsync 和 RefreshTokenAsync 超过 100 行
2. **职责混杂**：验证码管理、邮件发送、会话管理混在一起
3. **缺少领域事件**：登录成功、注册成功等事件未发布

## DDD 重构设计

### Repository 接口

```csharp
// BlogCore/Domain/Repositories/IVerificationCodeRepository.cs
public interface IVerificationCodeRepository
{
    /// <summary>
    /// 获取有效的验证码
    /// </summary>
    Task<VerificationCode?> GetValidCodeAsync(string email, string code);
    
    /// <summary>
    /// 添加验证码
    /// </summary>
    Task<VerificationCode> AddAsync(VerificationCode code);
    
    /// <summary>
    /// 标记验证码为已使用
    /// </summary>
    Task MarkAsUsedAsync(long codeId);
    
    /// <summary>
    /// 清理过期验证码
    /// </summary>
    Task CleanupExpiredAsync();
}
```

### Repository 实现

```csharp
// BlogCore/Infrastructure/Repositories/VerificationCodeRepository.cs
public class VerificationCodeRepository : IVerificationCodeRepository
{
    private readonly AppDbContext _dbContext;
    private readonly ILogger<VerificationCodeRepository> _logger;
    
    public VerificationCodeRepository(
        AppDbContext dbContext,
        ILogger<VerificationCodeRepository> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }
    
    public async Task<VerificationCode?> GetValidCodeAsync(string email, string code)
    {
        return await _dbContext.VerificationCodes
            .Where(v => v.Email == email 
                && v.Code == code 
                && !v.IsUsed 
                && v.ExpiresAt > DateTimeOffset.UtcNow)
            .OrderByDescending(v => v.CreateTime)
            .FirstOrDefaultAsync();
    }
    
    public async Task<VerificationCode> AddAsync(VerificationCode code)
    {
        await _dbContext.VerificationCodes.AddAsync(code);
        await _dbContext.SaveChangesAsync();
        return code;
    }
    
    public async Task MarkAsUsedAsync(long codeId)
    {
        await _dbContext.VerificationCodes
            .Where(v => v.Id == codeId)
            .ExecuteUpdateAsync(s => s
                .SetProperty(v => v.IsUsed, true)
                .SetProperty(v => v.UsedAt, DateTimeOffset.UtcNow));
    }
    
    public async Task CleanupExpiredAsync()
    {
        var threshold = DateTimeOffset.UtcNow.AddDays(-7);
        var deleted = await _dbContext.VerificationCodes
            .Where(v => v.ExpiresAt < threshold)
            .ExecuteDeleteAsync();
        
        if (deleted > 0)
        {
            _logger.LogInformation("清理过期验证码: {Count} 条", deleted);
        }
    }
}
```

### Domain Service

```csharp
// BlogCore/Domain/Services/IAuthDomainService.cs
public interface IAuthDomainService
{
    /// <summary>
    /// 验证用户凭据
    /// </summary>
    Task<AppUser> ValidateCredentialsAsync(string identifier, string password);
    
    /// <summary>
    /// 验证验证码
    /// </summary>
    Task<VerificationCode> ValidateVerificationCodeAsync(string email, string code);
    
    /// <summary>
    /// 生成验证码
    /// </summary>
    string GenerateVerificationCode();
}

// BlogCore/Domain/Services/AuthDomainService.cs
public class AuthDomainService : IAuthDomainService
{
    private readonly UserManager<AppUser> _userManager;
    private readonly SignInManager<AppUser> _signInManager;
    private readonly IVerificationCodeRepository _verificationCodeRepository;
    private readonly AppDbContext _dbContext;
    
    public async Task<AppUser> ValidateCredentialsAsync(string identifier, string password)
    {
        AppUser? user = null;
        
        // 按邮箱尝试
        if (identifier.Contains('@'))
        {
            user = await _userManager.FindByEmailAsync(identifier);
        }
        
        // 按昵称尝试
        if (user == null)
        {
            user = await _dbContext.Users.FirstOrDefaultAsync(u => u.NickName == identifier);
        }
        
        if (user == null)
            throw new BusinessException(BusinessError.UserNotFound);
        
        var result = await _signInManager.CheckPasswordSignInAsync(user, password, false);
        
        if (!result.Succeeded)
        {
            if (result.IsLockedOut)
            {
                if (user.LockoutEnd?.Value > DateTimeOffset.UtcNow.AddYears(50))
                    throw new BusinessException(BusinessError.UserLocked);
                
                var message = string.Format(BusinessError.UserLockedWithTime.Message, 
                    user.LockoutEnd.Value.ToLocalTime().ToString("yyyy-MM-dd HH:mm:ss"));
                throw new BusinessException(BusinessError.UserLockedWithTime.Code, message);
            }
            
            throw new BusinessException(BusinessError.WrongPassword);
        }
        
        return user;
    }
    
    public async Task<VerificationCode> ValidateVerificationCodeAsync(string email, string code)
    {
        var verificationCode = await _verificationCodeRepository.GetValidCodeAsync(email, code);
        
        if (verificationCode == null)
            throw new BusinessException(BusinessError.InvalidVerificationCode);
        
        return verificationCode;
    }
    
    public string GenerateVerificationCode()
    {
        var random = new Random();
        return random.Next(100000, 999999).ToString();
    }
}
```

### Application Service

```csharp
// BlogCore/Application/Auth/IAuthApplicationService.cs
public interface IAuthApplicationService
{
    Task RegisterAsync(RegisterReq req);
    Task<LoginResp> LoginAsync(LoginReq req);
    Task<RefreshTokenResp> RefreshTokenAsync(RefreshTokenReq req);
    Task LogoutAsync(string userId, string? sessionId = null);
    Task SendVerificationCodeAsync(string email, VerificationCodeType type);
    Task ResetPasswordAsync(ResetPasswordReq req);
    Task ChangePasswordAsync(string userId, ChangePasswordReq req);
}

// BlogCore/Application/Auth/AuthApplicationService.cs
public class AuthApplicationService : IAuthApplicationService
{
    private readonly IAuthDomainService _authDomainService;
    private readonly IVerificationCodeRepository _verificationCodeRepository;
    private readonly IJwtService _jwtService;
    private readonly ISessionService _sessionService;
    private readonly IEmailService _emailService;
    private readonly IDomainEventDispatcher _eventDispatcher;
    private readonly UserManager<AppUser> _userManager;
    private readonly IHttpContextAccessor _httpContextAccessor;
    private readonly ILogger<AuthApplicationService> _logger;
    
    public async Task<LoginResp> LoginAsync(LoginReq req)
    {
        // 1. 验证凭据
        var user = await _authDomainService.ValidateCredentialsAsync(req.Identifier, req.Password);
        
        // 2. 获取用户角色
        var roles = await _userManager.GetRolesAsync(user);
        
        // 3. 生成会话
        var sessionId = Guid.NewGuid().ToString("N");
        var token = _jwtService.GenerateToken(user, roles, sessionId);
        var refreshToken = _jwtService.GenerateRefreshTokenJwt(user.Id, sessionId);
        
        // 4. 创建会话记录
        var userAgent = _httpContextAccessor.HttpContext?.Request.Headers["User-Agent"].ToString();
        var ipAddress = _httpContextAccessor.HttpContext?.Connection.RemoteIpAddress?.ToString();
        var deviceInfo = DeviceInfoParser.FormatDeviceInfo(userAgent, ipAddress);
        
        await _sessionService.CreateSessionAsync(
            user.Id, 
            refreshToken.Token, 
            deviceInfo, 
            TimeSpan.FromMinutes(refreshToken.Expires.Subtract(DateTimeOffset.UtcNow).TotalMinutes),
            sessionId);
        
        // 5. 发布登录成功事件
        await _eventDispatcher.DispatchAsync(new UserLoggedInEvent
        {
            UserId = user.Id,
            SessionId = sessionId,
            DeviceInfo = deviceInfo,
            IpAddress = ipAddress
        });
        
        _logger.LogInformation("用户 {UserId} 登录成功，会话 {SessionId}", user.Id, sessionId);
        
        return new LoginResp
        {
            User = MapToUserDto(user, roles),
            Token = new RefreshTokenResp
            {
                Token = token,
                RefreshToken = refreshToken.Token,
                RefreshTokenExpires = refreshToken.Expires
            }
        };
    }
    
    public async Task RegisterAsync(RegisterReq req)
    {
        // 1. 验证验证码
        var verificationCode = await _authDomainService.ValidateVerificationCodeAsync(req.Email, req.VerificationCode);
        
        // 2. 检查邮箱和昵称唯一性
        // ... 省略验证逻辑
        
        // 3. 创建用户
        var user = new AppUser
        {
            UserName = req.Email,
            Email = req.Email,
            NickName = req.NickName
        };
        
        var result = await _userManager.CreateAsync(user, req.Password);
        if (!result.Succeeded)
            throw MapIdentityError(result.Errors.First());
        
        // 4. 分配角色
        await _userManager.AddToRoleAsync(user, "User");
        
        // 5. 标记验证码已使用
        await _verificationCodeRepository.MarkAsUsedAsync(verificationCode.Id);
        
        // 6. 发布注册成功事件
        await _eventDispatcher.DispatchAsync(new UserRegisteredEvent
        {
            UserId = user.Id,
            Email = user.Email,
            NickName = user.NickName
        });
        
        _logger.LogInformation("用户注册成功: {Email}", req.Email);
    }
}
```

## DTO 定义

### LoginReq

```csharp
/// <summary>
/// 登录请求
/// </summary>
public class LoginReq
{
    /// <summary>
    /// 登录标识（邮箱或昵称）
    /// </summary>
    [Required]
    public string Identifier { get; set; } = string.Empty;
    
    /// <summary>
    /// 密码
    /// </summary>
    [Required]
    public string Password { get; set; } = string.Empty;
}
```

### LoginResp

```csharp
/// <summary>
/// 登录响应
/// </summary>
public class LoginResp
{
    /// <summary>
    /// 用户信息
    /// </summary>
    public UserDto User { get; set; } = null!;
    
    /// <summary>
    /// Token 信息
    /// </summary>
    public RefreshTokenResp Token { get; set; } = null!;
}
```

### RegisterReq

```csharp
/// <summary>
/// 注册请求
/// </summary>
public class RegisterReq
{
    /// <summary>
    /// 邮箱
    /// </summary>
    [Required]
    [EmailAddress]
    public string Email { get; set; } = string.Empty;
    
    /// <summary>
    /// 昵称
    /// </summary>
    [Required]
    [StringLength(20, MinimumLength = 2)]
    public string NickName { get; set; } = string.Empty;
    
    /// <summary>
    /// 密码
    /// </summary>
    [Required]
    [StringLength(100, MinimumLength = 6)]
    public string Password { get; set; } = string.Empty;
    
    /// <summary>
    /// 验证码
    /// </summary>
    [Required]
    public string VerificationCode { get; set; } = string.Empty;
}
```

### RefreshTokenResp

```csharp
/// <summary>
/// Token 刷新响应
/// </summary>
public class RefreshTokenResp
{
    /// <summary>
    /// 访问令牌
    /// </summary>
    public string Token { get; set; } = string.Empty;
    
    /// <summary>
    /// 刷新令牌
    /// </summary>
    public string RefreshToken { get; set; } = string.Empty;
    
    /// <summary>
    /// 刷新令牌过期时间
    /// </summary>
    public DateTimeOffset RefreshTokenExpires { get; set; }
}
```

### VerificationCodeType

```csharp
/// <summary>
/// 验证码类型
/// </summary>
public enum VerificationCodeType
{
    /// <summary>
    /// 注册
    /// </summary>
    Register = 1,
    
    /// <summary>
    /// 重置密码
    /// </summary>
    ResetPassword = 2,
    
    /// <summary>
    /// 修改密码
    /// </summary>
    ChangePassword = 3
}
```

## 领域事件

### UserLoggedInEvent

```csharp
public record UserLoggedInEvent : DomainEventBase
{
    public override string EventType => nameof(UserLoggedInEvent);
    
    public string UserId { get; init; } = string.Empty;
    public string SessionId { get; init; } = string.Empty;
    public string? DeviceInfo { get; init; }
    public string? IpAddress { get; init; }
}
```

### UserRegisteredEvent

```csharp
public record UserRegisteredEvent : DomainEventBase
{
    public override string EventType => nameof(UserRegisteredEvent);
    
    public string UserId { get; init; } = string.Empty;
    public string Email { get; init; } = string.Empty;
    public string NickName { get; init; } = string.Empty;
}
```

### PasswordChangedEvent

```csharp
public record PasswordChangedEvent : DomainEventBase
{
    public override string EventType => nameof(PasswordChangedEvent);
    
    public string UserId { get; init; } = string.Empty;
}
```

## 缓存策略

### Redis 数据结构

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `session:{sessionId}` | String | 会话信息 | 7 天 |
| `user:{userId}:sessions` | Set | 用户会话列表 | 永久 |
| `token:blacklist:{jti}` | String | Token 黑名单 | Token 剩余有效期 |
| `verification:{email}` | String | 验证码发送频率限制 | 1 分钟 |

### 会话管理

```csharp
// 会话创建
await _redis.StringSetAsync($"session:{sessionId}", JsonSerializer.Serialize(sessionInfo), TimeSpan.FromDays(7));
await _redis.SetAddAsync($"user:{userId}:sessions", sessionId);

// 会话验证
var sessionData = await _redis.StringGetAsync($"session:{sessionId}");

// Token 黑名单
await _redis.StringSetAsync($"token:blacklist:{jti}", "1", remainingTime);
```

## 降级策略

### 邮件发送降级

```csharp
await _rabbitMqPolicyProvider.ExecuteWithFallbackAsync(
    async _ =>
    {
        await _eventPublisher.PublishEmailAsync(new EmailSendMessage
        {
            To = email,
            Subject = subject,
            HtmlBody = html
        });
    },
    fallbackAction: async _ =>
    {
        // MQ 不可用时，记录日志并抛出异常
        _logger.LogError("邮件发送失败：RabbitMQ 不可用");
        throw new BusinessException(BusinessError.EmailSendFailed);
    },
    operationKey: $"Auth:SendEmail:{email}");
```

### 会话服务降级

```csharp
// Redis 不可用时，会话验证降级到数据库
var session = await _redisPolicyProvider.ExecuteWithFallbackAsync(
    async _ => await GetSessionFromRedisAsync(sessionId),
    fallbackAction: async _ =>
    {
        _logger.LogWarning("Redis 不可用，会话验证降级到数据库");
        return await GetSessionFromDatabaseAsync(sessionId);
    },
    operationKey: $"Session:Get:{sessionId}");
```

## 依赖对比

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 依赖数量 | 10 | 8 |
| 方法行数 | LoginAsync 150+ 行 | LoginAsync 50 行 |
| 职责分离 | 混杂 | 清晰分层 |
| 领域事件 | 无 | 登录/注册/密码修改事件 |
| 可测试性 | 低 | 高（Domain Service 可独立测试） |
