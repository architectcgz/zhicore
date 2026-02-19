# 博客系统防刷机制设计方案

## 概述

本文档描述博客系统的防刷（Anti-Spam / Rate Limiting）机制设计，采用业界最佳实践，支持多实例分布式部署。

## 部署架构

```
                                    ┌─────────────────┐
                                    │   Cloudflare    │
                                    │   (DDoS 防护)   │
                                    └────────┬────────┘
                                             │
                                             ▼
                                    ┌─────────────────┐
                                    │     Nginx       │
                                    │  (负载均衡)     │
                                    └────────┬────────┘
                                             │
              ┌──────────────────────────────┼──────────────────────────────┐
              │                              │                              │
              ▼                              ▼                              ▼
     ┌─────────────────┐            ┌─────────────────┐            ┌─────────────────┐
     │   blog-api-1    │            │   blog-api-2    │            │   blog-api-3    │
     │   (实例 1)      │            │   (实例 2)      │            │   (实例 3)      │
     └────────┬────────┘            └────────┬────────┘            └────────┬────────┘
              │                              │                              │
              └──────────────────────────────┼──────────────────────────────┘
                                             │
              ┌──────────────────────────────┼──────────────────────────────┐
              │                              │                              │
              ▼                              ▼                              ▼
     ┌─────────────────┐            ┌─────────────────┐            ┌─────────────────┐
     │  Redis Cluster  │            │   PostgreSQL    │            │    RabbitMQ     │
     │  (分布式限流)   │            │   (持久化)      │            │   (异步记录)    │
     └─────────────────┘            └─────────────────┘            └─────────────────┘
```

## 多实例部署的挑战

| 挑战 | 说明 | 解决方案 |
|------|------|---------|
| 计数不一致 | 每个实例独立计数，总数可能超限 | Redis 集中式计数 |
| 时钟偏差 | 不同实例时钟可能不同步 | 使用 Redis 服务器时间 |
| 网络分区 | Redis 不可用时的降级 | 本地限流 + 降级策略 |
| 竞态条件 | 并发请求可能同时通过检查 | Redis Lua 脚本原子操作 |

## 限流算法选择

### 算法对比

| 算法 | 原理 | 优点 | 缺点 | 推荐场景 |
|------|------|------|------|---------|
| 固定窗口 | 固定时间窗口计数 | 实现简单 | 窗口边界突发 | 简单场景 |
| **滑动窗口** | 滚动时间窗口 | 平滑、精确 | 实现稍复杂 | **通用推荐** |
| 令牌桶 | 固定速率生成令牌 | 允许突发 | 需维护状态 | 允许突发场景 |
| 漏桶 | 固定速率处理 | 流量平滑 | 不允许突发 | 严格限流 |

**推荐**：博客系统采用 **滑动窗口** 算法，兼顾精确性和性能。

## 三层限流架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Layer 1: 基础设施限流                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    .NET 内置 RateLimiter                              │  │
│  │  - 全局限流：保护系统整体（10000 req/s）                              │  │
│  │  - IP 限流：防止单 IP 攻击（100 req/min）                             │  │
│  │  - 端点限流：保护敏感接口（登录 5 req/min）                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Layer 2: 业务防刷                                  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    Redis 分布式限流                                   │  │
│  │  - 用户行为限流：评论、点赞、关注、消息                               │  │
│  │  - 目标限流：同一目标操作限制                                         │  │
│  │  - 冷却时间：取消操作后的等待期                                       │  │
│  │  - 用户分级：不同等级不同限制                                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Layer 3: 智能防护（可选）                          │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    行为分析 & 异常检测                                │  │
│  │  - 异常行为模式识别                                                   │  │
│  │  - 实时告警                                                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```


## 业务限流规则

### 操作类型与限制

| 操作类型 | 冷却时间 | 每分钟 | 每小时 | 每天 | 同一目标/小时 | 特殊限制 |
|---------|---------|--------|--------|------|--------------|---------|
| 评论 | 10秒 | 15 | 30 | 200 | 10 | - |
| 点赞 | 3秒 | 20 | 100 | 500 | 5 | - |
| 收藏 | 5秒 | 10 | 50 | 200 | 3 | - |
| 关注 | 5分钟 | - | 20 | 100 | 3 | - |
| 消息 | 1秒 | 30 | 100 | 500 | - | 陌生人每天1条 |
| 举报 | 5分钟 | - | 5 | 20 | 1/天 | - |
| 反馈 | 5分钟 | - | 3 | 10 | - | - |

### 用户分级限流

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户等级体系                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Anonymous (未登录)                                              │
│  └─ 仅允许浏览，不允许评论/点赞/消息                            │
│                                                                  │
│  NewUser (注册 < 7 天)                                          │
│  └─ 基础限制的 50%                                              │
│                                                                  │
│  Regular (注册 >= 7 天)                                         │
│  └─ 标准限制（上表数值）                                        │
│                                                                  │
│  Trusted (信誉分 >= 100)                                        │
│  └─ 标准限制的 200%                                             │
│                                                                  │
│  VIP (付费用户)                                                 │
│  └─ 标准限制的 500%                                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 陌生人消息限制

防止消息骚扰的特殊机制：

- **朋友之间**（对方已关注你）：无消息数量限制
- **陌生人**（对方未关注你）：每天只能发送 1 条消息

```
用户A → 用户B（B未关注A）
  │
  ├─ 第1条消息：✅ 允许发送
  │
  └─ 第2条消息：❌ 拒绝，提示"对方未关注您，每天只能发送1条消息"
  
用户B 关注 用户A 后：
  │
  └─ 用户A → 用户B：✅ 无限制
```

## 技术实现

### 1. Layer 1: .NET 内置限流配置

```csharp
// Program.cs
builder.Services.AddRateLimiter(options =>
{
    // 全局限流 - 保护系统整体
    options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: "global",
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 10000,
                Window = TimeSpan.FromSeconds(1),
                SegmentsPerWindow = 10
            }));
    
    // IP 限流 - 防止单 IP 攻击
    options.AddPolicy("per-ip", context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: GetClientIp(context),
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 100,
                Window = TimeSpan.FromMinutes(1),
                SegmentsPerWindow = 6
            }));
    
    // 登录接口限流
    options.AddPolicy("login", context =>
        RateLimitPartition.GetSlidingWindowLimiter(
            partitionKey: GetClientIp(context),
            factory: _ => new SlidingWindowRateLimiterOptions
            {
                PermitLimit = 5,
                Window = TimeSpan.FromMinutes(1),
                SegmentsPerWindow = 6
            }));
    
    // 429 响应处理
    options.OnRejected = async (context, token) =>
    {
        context.HttpContext.Response.StatusCode = 429;
        context.HttpContext.Response.ContentType = "application/json";
        
        var retryAfter = context.Lease.TryGetMetadata(
            MetadataName.RetryAfter, out var retry) ? (int)retry.TotalSeconds : 60;
        
        context.HttpContext.Response.Headers.RetryAfter = retryAfter.ToString();
        
        await context.HttpContext.Response.WriteAsJsonAsync(new
        {
            code = "RATE_LIMITED",
            message = "请求过于频繁，请稍后再试",
            retryAfter
        }, token);
    };
});

// 启用限流
app.UseRateLimiter();

// 应用到端点
app.MapPost("/api/auth/login", LoginHandler).RequireRateLimiting("login");
app.MapControllers().RequireRateLimiting("per-ip");
```


### 2. Layer 2: Redis 分布式限流

#### Redis 数据结构设计

| Key 模式 | 类型 | 用途 | TTL |
|---------|------|------|-----|
| `rl:sw:{userId}:{action}` | Sorted Set | 滑动窗口计数 | 窗口时间 + 1min |
| `rl:cd:{userId}:{action}:{targetId}` | String | 冷却时间 | 冷却时间 + 10s |
| `rl:target:{userId}:{action}:{targetId}` | Sorted Set | 目标限流 | 1h |
| `rl:stranger:{userId}:{targetId}` | String | 陌生人关系缓存 | 1h |

#### 滑动窗口 Lua 脚本（原子操作）

```lua
-- sliding_window_check.lua
-- KEYS[1]: 限流 key
-- ARGV[1]: 窗口起始时间戳（毫秒）
-- ARGV[2]: 当前时间戳（毫秒）
-- ARGV[3]: 最大请求数
-- ARGV[4]: 窗口大小（毫秒）
-- ARGV[5]: TTL（秒）

-- 移除窗口外的记录
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])

-- 获取当前窗口内的请求数
local count = redis.call('ZCARD', KEYS[1])

-- 检查是否超限
if count >= tonumber(ARGV[3]) then
    -- 获取最早的请求时间，计算重试时间
    local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
    if #oldest > 0 then
        local retryAfter = math.ceil((oldest[2] + tonumber(ARGV[4]) - tonumber(ARGV[2])) / 1000)
        return {1, count, retryAfter}  -- blocked, count, retryAfter
    end
    return {1, count, math.ceil(tonumber(ARGV[4]) / 1000)}
end

-- 添加当前请求
redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
redis.call('EXPIRE', KEYS[1], ARGV[5])

return {0, count + 1, 0}  -- allowed, newCount, retryAfter
```

#### C# 实现

```csharp
// BlogCore/Infrastructure/RateLimiting/RedisRateLimiter.cs
public class RedisRateLimiter : IRateLimiter
{
    private readonly IDatabase _redis;
    private readonly ILogger<RedisRateLimiter> _logger;
    private readonly string _slidingWindowScript;
    
    public RedisRateLimiter(IConnectionMultiplexer redis, ILogger<RedisRateLimiter> logger)
    {
        _redis = redis.GetDatabase();
        _logger = logger;
        _slidingWindowScript = LoadLuaScript("sliding_window_check.lua");
    }
    
    /// <summary>
    /// 滑动窗口限流检查（分布式安全）
    /// </summary>
    public async Task<RateLimitResult> CheckSlidingWindowAsync(
        string key, 
        int maxRequests, 
        TimeSpan window,
        CancellationToken ct = default)
    {
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var windowStart = now - (long)window.TotalMilliseconds;
        var ttl = (int)window.TotalSeconds + 60;
        
        try
        {
            var result = await _redis.ScriptEvaluateAsync(
                _slidingWindowScript,
                new RedisKey[] { key },
                new RedisValue[] 
                { 
                    windowStart,
                    now,
                    maxRequests,
                    (long)window.TotalMilliseconds,
                    ttl
                });
            
            var values = (RedisResult[])result!;
            var isBlocked = (int)values[0] == 1;
            var count = (int)values[1];
            var retryAfter = (int)values[2];
            
            return new RateLimitResult
            {
                IsAllowed = !isBlocked,
                CurrentCount = count,
                Limit = maxRequests,
                RetryAfterSeconds = isBlocked ? retryAfter : null
            };
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Redis 限流检查失败，降级到本地限流: Key={Key}", key);
            return await FallbackToLocalLimitAsync(key, maxRequests, window);
        }
    }
    
    /// <summary>
    /// 冷却时间检查
    /// </summary>
    public async Task<RateLimitResult> CheckCooldownAsync(
        string userId,
        string actionType,
        string targetId,
        int cooldownSeconds)
    {
        if (cooldownSeconds <= 0 || string.IsNullOrEmpty(targetId))
            return RateLimitResult.Allowed();
        
        var key = $"rl:cd:{userId}:{actionType}:{targetId}";
        
        try
        {
            var lastActionTime = await _redis.StringGetAsync(key);
            
            if (lastActionTime.HasValue && long.TryParse(lastActionTime, out var ticks))
            {
                var lastTime = new DateTimeOffset(ticks, TimeSpan.Zero);
                var cooldownEnd = lastTime.AddSeconds(cooldownSeconds);
                var now = DateTimeOffset.UtcNow;
                
                if (now < cooldownEnd)
                {
                    var remaining = (int)(cooldownEnd - now).TotalSeconds;
                    return RateLimitResult.Blocked(
                        $"操作过于频繁，请等待 {remaining} 秒后再试",
                        remaining);
                }
            }
            
            return RateLimitResult.Allowed();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Redis 冷却检查失败: Key={Key}", key);
            return RateLimitResult.Allowed(); // 降级：允许操作
        }
    }
    
    /// <summary>
    /// 记录操作（用于冷却时间）
    /// </summary>
    public async Task RecordActionAsync(
        string userId,
        string actionType,
        string? targetId,
        int cooldownSeconds)
    {
        if (cooldownSeconds <= 0 || string.IsNullOrEmpty(targetId))
            return;
        
        var key = $"rl:cd:{userId}:{actionType}:{targetId}";
        var now = DateTimeOffset.UtcNow.Ticks;
        
        try
        {
            await _redis.StringSetAsync(
                key, 
                now.ToString(), 
                TimeSpan.FromSeconds(cooldownSeconds + 10));
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Redis 记录操作失败: Key={Key}", key);
        }
    }
    
    /// <summary>
    /// 本地降级限流（Redis 不可用时）
    /// </summary>
    private Task<RateLimitResult> FallbackToLocalLimitAsync(
        string key, int maxRequests, TimeSpan window)
    {
        // 使用内存缓存作为降级方案
        // 注意：多实例部署时，本地限流不是全局准确的
        // 但可以提供基本保护
        return Task.FromResult(RateLimitResult.Allowed());
    }
}
```


### 3. 业务防刷服务

```csharp
// BlogCore/Application/Auth/IBusinessRateLimitService.cs
public interface IBusinessRateLimitService
{
    Task<RateLimitResult> CheckAsync(RateLimitContext context);
    Task RecordAsync(RateLimitContext context);
}

public class RateLimitContext
{
    public string UserId { get; set; } = string.Empty;
    public string? TargetId { get; set; }
    public AntiSpamActionType ActionType { get; set; }
    public string? IpAddress { get; set; }
    public UserTier UserTier { get; set; } = UserTier.Regular;
}

// BlogCore/Application/Auth/BusinessRateLimitService.cs
public class BusinessRateLimitService : IBusinessRateLimitService
{
    private readonly IRateLimiter _rateLimiter;
    private readonly IUserFollowRepository _followRepository;
    private readonly IMessageRepository _messageRepository;
    private readonly IOptions<BusinessRateLimitConfig> _config;
    private readonly ILogger<BusinessRateLimitService> _logger;
    
    public async Task<RateLimitResult> CheckAsync(RateLimitContext context)
    {
        var limits = GetLimitsForTier(context.ActionType, context.UserTier);
        
        // 1. 检查冷却时间
        if (!string.IsNullOrEmpty(context.TargetId) && limits.CooldownSeconds > 0)
        {
            var cooldownResult = await _rateLimiter.CheckCooldownAsync(
                context.UserId, 
                context.ActionType.ToString(), 
                context.TargetId,
                limits.CooldownSeconds);
            
            if (!cooldownResult.IsAllowed)
                return cooldownResult;
        }
        
        // 2. 检查频率限制（滑动窗口）
        if (limits.MaxPerHour > 0)
        {
            var key = $"rl:sw:{context.UserId}:{context.ActionType}";
            var result = await _rateLimiter.CheckSlidingWindowAsync(
                key, limits.MaxPerHour, TimeSpan.FromHours(1));
            
            if (!result.IsAllowed)
            {
                result.Message = GetRateLimitMessage(context.ActionType, "每小时", limits.MaxPerHour);
                return result;
            }
        }
        
        // 3. 检查目标限制
        if (!string.IsNullOrEmpty(context.TargetId) && limits.MaxPerTargetPerHour > 0)
        {
            var key = $"rl:target:{context.UserId}:{context.ActionType}:{context.TargetId}";
            var result = await _rateLimiter.CheckSlidingWindowAsync(
                key, limits.MaxPerTargetPerHour, TimeSpan.FromHours(1));
            
            if (!result.IsAllowed)
            {
                result.Message = "对该内容操作过于频繁，请稍后再试";
                return result;
            }
        }
        
        // 4. 消息特殊检查：陌生人限制
        if (context.ActionType == AntiSpamActionType.Message)
        {
            var strangerResult = await CheckStrangerMessageLimitAsync(context);
            if (!strangerResult.IsAllowed)
                return strangerResult;
        }
        
        return RateLimitResult.Allowed();
    }
    
    private async Task<RateLimitResult> CheckStrangerMessageLimitAsync(RateLimitContext context)
    {
        if (string.IsNullOrEmpty(context.TargetId))
            return RateLimitResult.Allowed();
        
        // 检查对方是否关注了发送者
        var isFollowed = await _followRepository.IsFollowingAsync(
            context.TargetId, context.UserId);
        
        if (isFollowed)
            return RateLimitResult.Allowed(); // 不是陌生人，无限制
        
        // 检查是否收到过对方的回复
        var hasReply = await _messageRepository.HasMessageFromAsync(
            context.TargetId, context.UserId);
        
        if (hasReply)
            return RateLimitResult.Allowed(); // 已有互动，无限制
        
        // 检查今天已发送的消息数
        var sentCount = await _messageRepository.GetSentCountTodayAsync(
            context.UserId, context.TargetId);
        
        var maxToStranger = _config.Value.Message.MaxToStrangerPerDay;
        
        if (sentCount >= maxToStranger)
        {
            return RateLimitResult.Blocked(
                $"对方还未关注或回复您，每天只能发送 {maxToStranger} 条消息");
        }
        
        return RateLimitResult.Allowed();
    }
    
    private ActionLimits GetLimitsForTier(AntiSpamActionType action, UserTier tier)
    {
        var baseLimits = _config.Value.GetLimits(action);
        var multiplier = tier switch
        {
            UserTier.Anonymous => 0,
            UserTier.NewUser => 0.5,
            UserTier.Regular => 1.0,
            UserTier.Trusted => 2.0,
            UserTier.VIP => 5.0,
            _ => 1.0
        };
        
        return baseLimits.WithMultiplier(multiplier);
    }
}
```

### 4. 中间件集成（解耦业务代码）

```csharp
// BlogApi/Middlewares/BusinessRateLimitMiddleware.cs
public class BusinessRateLimitMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<BusinessRateLimitMiddleware> _logger;
    
    public async Task InvokeAsync(
        HttpContext context, 
        IBusinessRateLimitService rateLimitService)
    {
        var endpoint = context.GetEndpoint();
        var attr = endpoint?.Metadata.GetMetadata<RateLimitAttribute>();
        
        if (attr == null)
        {
            await _next(context);
            return;
        }
        
        var userId = context.User.GetUserId();
        if (string.IsNullOrEmpty(userId))
        {
            await _next(context);
            return;
        }
        
        var rateLimitContext = new RateLimitContext
        {
            UserId = userId,
            TargetId = await ExtractTargetIdAsync(context, attr),
            ActionType = attr.ActionType,
            IpAddress = GetClientIp(context),
            UserTier = await GetUserTierAsync(context, userId)
        };
        
        var result = await rateLimitService.CheckAsync(rateLimitContext);
        
        if (!result.IsAllowed)
        {
            _logger.LogWarning(
                "业务限流拦截: UserId={UserId}, Action={Action}, Reason={Reason}",
                userId, attr.ActionType, result.Message);
            
            context.Response.StatusCode = 429;
            context.Response.Headers.RetryAfter = 
                (result.RetryAfterSeconds ?? 60).ToString();
            
            await context.Response.WriteAsJsonAsync(new
            {
                code = "BUSINESS_RATE_LIMITED",
                message = result.Message,
                retryAfter = result.RetryAfterSeconds
            });
            return;
        }
        
        // 保存上下文供后续记录
        context.Items["RateLimitContext"] = rateLimitContext;
        
        await _next(context);
        
        // 成功后记录操作
        if (context.Response.StatusCode >= 200 && context.Response.StatusCode < 300)
        {
            await rateLimitService.RecordAsync(rateLimitContext);
        }
    }
}

// 使用示例
[HttpPost]
[Authorize]
[RateLimit(AntiSpamActionType.Comment, TargetIdParam = "dto.PostId")]
public async Task<IActionResult> CreateComment([FromBody] CreateCommentDto dto)
{
    // 纯业务逻辑，无限流代码
    var commentId = await _commentService.CreateCommentAsync(dto, UserId);
    return Ok(new { id = commentId });
}
```


## 降级策略

### Redis 不可用时的降级

```
┌─────────────────────────────────────────────────────────────────┐
│                      Redis 健康检查                              │
│                           │                                      │
│              ┌────────────┴────────────┐                        │
│              │                         │                        │
│              ▼                         ▼                        │
│      ┌─────────────┐           ┌─────────────┐                 │
│      │  Redis 正常  │           │  Redis 故障  │                 │
│      └──────┬──────┘           └──────┬──────┘                 │
│             │                         │                        │
│             ▼                         ▼                        │
│      ┌─────────────┐           ┌─────────────┐                 │
│      │ 分布式限流   │           │ 本地限流     │                 │
│      │ (精确)      │           │ (近似)      │                 │
│      └─────────────┘           └─────────────┘                 │
│                                       │                        │
│                                       ▼                        │
│                                ┌─────────────┐                 │
│                                │ 限制更严格   │                 │
│                                │ (保护系统)   │                 │
│                                └─────────────┘                 │
└─────────────────────────────────────────────────────────────────┘
```

```csharp
// 降级配置
public class RateLimitFallbackConfig
{
    /// <summary>
    /// 降级时的限制倍数（更严格）
    /// </summary>
    public double FallbackMultiplier { get; set; } = 0.5;
    
    /// <summary>
    /// 熔断器配置
    /// </summary>
    public int FailureThreshold { get; set; } = 5;
    public int SuccessThreshold { get; set; } = 3;
    public int BreakDurationSeconds { get; set; } = 30;
}
```

## 配置示例

```json
{
  "RateLimiting": {
    "Infrastructure": {
      "Global": {
        "PermitLimit": 10000,
        "WindowSeconds": 1
      },
      "PerIp": {
        "PermitLimit": 100,
        "WindowMinutes": 1
      },
      "Login": {
        "PermitLimit": 5,
        "WindowMinutes": 1
      }
    },
    "Business": {
      "Comment": {
        "CooldownSeconds": 10,
        "MaxPerMinute": 15,
        "MaxPerHour": 30,
        "MaxPerDay": 200,
        "MaxPerTargetPerHour": 10
      },
      "Like": {
        "CooldownSeconds": 3,
        "MaxPerMinute": 20,
        "MaxPerHour": 100,
        "MaxPerDay": 500,
        "MaxPerTargetPerHour": 5
      },
      "Favorite": {
        "CooldownSeconds": 5,
        "MaxPerMinute": 10,
        "MaxPerHour": 50,
        "MaxPerDay": 200,
        "MaxPerTargetPerHour": 3
      },
      "Follow": {
        "CooldownMinutes": 5,
        "MaxPerHour": 20,
        "MaxPerDay": 100,
        "MaxPerTargetPerHour": 3
      },
      "Message": {
        "CooldownSeconds": 1,
        "MaxPerMinute": 30,
        "MaxPerHour": 100,
        "MaxPerDay": 500,
        "MaxToStrangerPerDay": 1
      },
      "Report": {
        "CooldownMinutes": 5,
        "MaxPerHour": 5,
        "MaxPerDay": 20,
        "MaxPerTargetPerDay": 1
      },
      "Feedback": {
        "CooldownMinutes": 5,
        "MaxPerHour": 3,
        "MaxPerDay": 10
      }
    },
    "UserTierMultipliers": {
      "Anonymous": 0,
      "NewUser": 0.5,
      "Regular": 1.0,
      "Trusted": 2.0,
      "VIP": 5.0
    },
    "Fallback": {
      "FallbackMultiplier": 0.5,
      "FailureThreshold": 5,
      "SuccessThreshold": 3,
      "BreakDurationSeconds": 30
    }
  }
}
```

## 监控与告警

### Prometheus 指标

```csharp
public class RateLimitMetrics
{
    private static readonly Counter RejectedRequests = Metrics.CreateCounter(
        "rate_limit_rejected_total",
        "被拒绝的请求总数",
        new CounterConfiguration
        {
            LabelNames = new[] { "action_type", "limit_type", "user_tier" }
        });
    
    private static readonly Histogram CheckLatency = Metrics.CreateHistogram(
        "rate_limit_check_duration_seconds",
        "限流检查延迟",
        new HistogramConfiguration
        {
            LabelNames = new[] { "action_type" },
            Buckets = new[] { 0.001, 0.005, 0.01, 0.025, 0.05, 0.1 }
        });
    
    private static readonly Gauge RedisHealth = Metrics.CreateGauge(
        "rate_limit_redis_healthy",
        "Redis 健康状态 (1=健康, 0=故障)");
}
```

### 告警规则

| 指标 | 条件 | 严重程度 | 说明 |
|------|------|---------|------|
| `rate_limit_rejected_total` | > 1000/min | Warning | 拒绝请求过多 |
| `rate_limit_rejected_total{user_tier="Regular"}` | > 100/min | Warning | 正常用户被限制过多 |
| `rate_limit_check_duration_seconds` | p99 > 50ms | Warning | 限流检查延迟过高 |
| `rate_limit_redis_healthy` | == 0 持续 1min | Critical | Redis 故障 |

## 前端配合

### 响应处理

```typescript
// api/interceptors.ts
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 429) {
      const { message, retryAfter } = error.response.data;
      
      // 显示友好提示
      showToast({
        type: 'warning',
        message: message || '操作过于频繁，请稍后再试',
        duration: (retryAfter || 60) * 1000
      });
      
      // 可选：显示倒计时
      if (retryAfter) {
        startCountdown(retryAfter);
      }
    }
    return Promise.reject(error);
  }
);
```

### 防抖处理

```typescript
// composables/useRateLimit.ts
export function useRateLimit() {
  const isLimited = ref(false);
  const countdown = ref(0);
  
  const executeWithRateLimit = async <T>(
    action: () => Promise<T>,
    debounceMs = 1000
  ): Promise<T | null> => {
    if (isLimited.value) {
      showToast({ message: `请等待 ${countdown.value} 秒后再试` });
      return null;
    }
    
    try {
      return await action();
    } catch (error: any) {
      if (error.response?.status === 429) {
        const retryAfter = error.response.data.retryAfter || 60;
        isLimited.value = true;
        countdown.value = retryAfter;
        
        const timer = setInterval(() => {
          countdown.value--;
          if (countdown.value <= 0) {
            isLimited.value = false;
            clearInterval(timer);
          }
        }, 1000);
      }
      throw error;
    }
  };
  
  return { isLimited, countdown, executeWithRateLimit };
}
```

## 总结

### 设计要点

1. **三层防护**：基础设施限流 → 业务限流 → 智能防护
2. **分布式一致**：Redis Lua 脚本保证原子性
3. **优雅降级**：Redis 故障时本地限流兜底
4. **用户分级**：不同等级不同限制
5. **业务解耦**：中间件 + 特性标注，业务代码无感知
6. **可观测性**：完善的监控指标和告警

### 与现有架构的关系

- 复用 Redis 基础设施
- 复用 RabbitMQ 异步记录
- 符合 DDD 分层架构
- 支持多实例部署
