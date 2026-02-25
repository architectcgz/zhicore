# 防刷机制解耦设计方案

## 概述

本文档描述如何将防刷（Anti-Spam）机制从业务层解耦，采用 AOP 切面 + 中间件的方式实现横切关注点的分离，使业务代码保持纯净。

## 当前问题分析

### 现有实现的耦合问题

```csharp
// 当前方式：业务层显式调用防刷服务
public class CommentService : ICommentService
{
    private readonly IAntiSpamService _antiSpamService;
    
    public async Task<long> CreateCommentAsync(CreateCommentDto dto, string userId, ...)
    {
        // ❌ 问题1：业务代码需要知道防刷逻辑
        var antiSpamResult = await _antiSpamService.CheckActionAsync(
            AntiSpamActionType.Comment, userId, dto.PostId.ToString());
        if (antiSpamResult.IsBlocked)
            throw new InvalidOperationException(antiSpamResult.Reason);
        
        // 业务逻辑...
        var comment = new Comment { ... };
        await dbContext.SaveChangesAsync();
        
        // ❌ 问题2：容易遗漏记录操作
        await _antiSpamService.RecordActionAsync(
            AntiSpamActionType.Comment, userId, dto.PostId.ToString());
        
        return comment.Id;
    }
}
```

### 问题总结

| 问题 | 影响 |
|------|------|
| 显式调用 | 每个业务方法都要手动调用检查和记录 |
| 容易遗漏 | 新增业务方法时可能忘记添加防刷检查 |
| 代码污染 | 业务代码被防刷逻辑污染，职责不清 |
| 难以统一修改 | 修改防刷策略需要改动多处代码 |
| 测试困难 | 业务逻辑和防刷逻辑耦合，难以独立测试 |

## 解耦方案设计

### 架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                    Presentation Layer                            │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              AntiSpamMiddleware (HTTP 层拦截)               ││
│  │  - 基于路由元数据自动检查                                    ││
│  │  - 返回 429 Too Many Requests                               ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Controllers                               ││
│  │  [AntiSpam(ActionType.Comment, TargetIdParam = "dto.PostId")]││
│  │  public async Task<IActionResult> CreateComment(...)         ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Application Layer                             │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │           AntiSpamBehavior (MediatR Pipeline)               ││
│  │  - 拦截 IRequest<T> 命令                                    ││
│  │  - 自动检查和记录                                           ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              Application Services (纯业务逻辑)              ││
│  │  - 无防刷代码                                               ││
│  │  - 专注业务编排                                             ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Domain Layer                                │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              IAntiSpamDomainService                         ││
│  │  - CheckCooldownAsync()                                     ││
│  │  - CheckRateLimitAsync()                                    ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Redis Cache  │  │  PostgreSQL  │  │   RabbitMQ   │          │
│  │  (计数器)    │  │  (历史记录)  │  │  (异步记录)  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

## 方案一：特性标注 + 中间件（推荐）

### 1. 定义防刷特性

```csharp
// ZhiCoreCore/Application/Attributes/AntiSpamAttribute.cs
namespace ZhiCoreCore.Application.Attributes;

/// <summary>
/// 防刷特性，标注在 Controller Action 或 Application Service 方法上
/// </summary>
[AttributeUsage(AttributeTargets.Method | AttributeTargets.Class, AllowMultiple = false)]
public class AntiSpamAttribute : Attribute
{
    /// <summary>
    /// 操作类型
    /// </summary>
    public AntiSpamActionType ActionType { get; }
    
    /// <summary>
    /// 目标ID参数名（支持嵌套属性，如 "dto.PostId"）
    /// </summary>
    public string? TargetIdParam { get; set; }
    
    /// <summary>
    /// 用户ID参数名（默认从 HttpContext.User 获取）
    /// </summary>
    public string? UserIdParam { get; set; }
    
    /// <summary>
    /// 是否在操作成功后记录（默认 true）
    /// </summary>
    public bool RecordOnSuccess { get; set; } = true;
    
    /// <summary>
    /// 自定义错误消息
    /// </summary>
    public string? CustomErrorMessage { get; set; }
    
    public AntiSpamAttribute(AntiSpamActionType actionType)
    {
        ActionType = actionType;
    }
}
```

### 2. 实现防刷中间件

```csharp
// ZhiCoreApi/Middlewares/AntiSpamMiddleware.cs
namespace ZhiCoreApi.Middlewares;

/// <summary>
/// 防刷中间件 - 在 HTTP 请求层统一拦截
/// </summary>
public class AntiSpamMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<AntiSpamMiddleware> _logger;
    
    public AntiSpamMiddleware(RequestDelegate next, ILogger<AntiSpamMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }
    
    public async Task InvokeAsync(HttpContext context, IAntiSpamApplicationService antiSpamService)
    {
        var endpoint = context.GetEndpoint();
        var antiSpamAttr = endpoint?.Metadata.GetMetadata<AntiSpamAttribute>();
        
        // 没有标注防刷特性，直接放行
        if (antiSpamAttr == null)
        {
            await _next(context);
            return;
        }
        
        // 获取用户ID
        var userId = GetUserId(context, antiSpamAttr);
        if (string.IsNullOrEmpty(userId))
        {
            // 未登录用户，跳过防刷检查（或根据策略处理）
            await _next(context);
            return;
        }
        
        // 获取目标ID
        var targetId = await GetTargetIdAsync(context, antiSpamAttr);
        
        // 获取客户端IP
        var ipAddress = GetClientIp(context);
        
        // 执行防刷检查
        var checkResult = await antiSpamService.CheckActionAsync(
            antiSpamAttr.ActionType, userId, targetId, ipAddress);
        
        if (checkResult.IsBlocked)
        {
            _logger.LogWarning(
                "防刷拦截: UserId={UserId}, ActionType={ActionType}, Reason={Reason}",
                userId, antiSpamAttr.ActionType, checkResult.Reason);
            
            context.Response.StatusCode = StatusCodes.Status429TooManyRequests;
            context.Response.ContentType = "application/json";
            
            var response = new AntiSpamErrorResponse
            {
                Message = antiSpamAttr.CustomErrorMessage ?? checkResult.Reason,
                LimitType = checkResult.LimitType?.ToString(),
                CooldownSeconds = checkResult.CooldownSeconds,
                RetryAfter = checkResult.CooldownSeconds
            };
            
            // 添加 Retry-After 响应头
            if (checkResult.CooldownSeconds.HasValue)
            {
                context.Response.Headers.RetryAfter = checkResult.CooldownSeconds.Value.ToString();
            }
            
            await context.Response.WriteAsJsonAsync(response);
            return;
        }
        
        // 保存防刷上下文，供后续记录使用
        context.Items["AntiSpam:ActionType"] = antiSpamAttr.ActionType;
        context.Items["AntiSpam:UserId"] = userId;
        context.Items["AntiSpam:TargetId"] = targetId;
        context.Items["AntiSpam:IpAddress"] = ipAddress;
        context.Items["AntiSpam:RecordOnSuccess"] = antiSpamAttr.RecordOnSuccess;
        
        await _next(context);
        
        // 操作成功后记录（仅在 2xx 响应时）
        if (antiSpamAttr.RecordOnSuccess && 
            context.Response.StatusCode >= 200 && 
            context.Response.StatusCode < 300)
        {
            await antiSpamService.RecordActionAsync(
                antiSpamAttr.ActionType, userId, targetId, ipAddress);
        }
    }
    
    private string? GetUserId(HttpContext context, AntiSpamAttribute attr)
    {
        // 优先从参数获取
        if (!string.IsNullOrEmpty(attr.UserIdParam))
        {
            return context.Request.RouteValues[attr.UserIdParam]?.ToString();
        }
        
        // 从 JWT Claims 获取
        return context.User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
    }
    
    private async Task<string?> GetTargetIdAsync(HttpContext context, AntiSpamAttribute attr)
    {
        if (string.IsNullOrEmpty(attr.TargetIdParam))
            return null;
        
        // 从路由参数获取
        if (context.Request.RouteValues.TryGetValue(attr.TargetIdParam, out var routeValue))
        {
            return routeValue?.ToString();
        }
        
        // 从查询参数获取
        if (context.Request.Query.TryGetValue(attr.TargetIdParam, out var queryValue))
        {
            return queryValue.ToString();
        }
        
        // 从请求体获取（需要启用 Request Body Buffering）
        if (context.Request.HasJsonContentType())
        {
            context.Request.EnableBuffering();
            var body = await JsonSerializer.DeserializeAsync<JsonElement>(context.Request.Body);
            context.Request.Body.Position = 0;
            
            return GetNestedProperty(body, attr.TargetIdParam);
        }
        
        return null;
    }
    
    private string? GetNestedProperty(JsonElement element, string path)
    {
        var parts = path.Split('.');
        var current = element;
        
        foreach (var part in parts)
        {
            if (current.TryGetProperty(part, out var next) ||
                current.TryGetProperty(ToCamelCase(part), out next))
            {
                current = next;
            }
            else
            {
                return null;
            }
        }
        
        return current.ValueKind switch
        {
            JsonValueKind.String => current.GetString(),
            JsonValueKind.Number => current.GetRawText(),
            _ => null
        };
    }
    
    private string ToCamelCase(string str) =>
        string.IsNullOrEmpty(str) ? str : char.ToLowerInvariant(str[0]) + str[1..];
    
    private string? GetClientIp(HttpContext context)
    {
        // 优先从 X-Forwarded-For 获取（反向代理场景）
        var forwardedFor = context.Request.Headers["X-Forwarded-For"].FirstOrDefault();
        if (!string.IsNullOrEmpty(forwardedFor))
        {
            return forwardedFor.Split(',')[0].Trim();
        }
        
        return context.Connection.RemoteIpAddress?.ToString();
    }
}

/// <summary>
/// 防刷错误响应
/// </summary>
public class AntiSpamErrorResponse
{
    public string Message { get; set; } = string.Empty;
    public string? LimitType { get; set; }
    public int? CooldownSeconds { get; set; }
    public int? RetryAfter { get; set; }
}
```

### 3. Controller 使用示例

```csharp
// ZhiCoreApi/Controllers/CommentController.cs
[ApiController]
[Route("api/comments")]
public class CommentController : ControllerBase
{
    private readonly ICommentApplicationService _commentService;
    
    [HttpPost]
    [Authorize]
    [AntiSpam(AntiSpamActionType.Comment, TargetIdParam = "dto.PostId")]
    public async Task<IActionResult> CreateComment([FromBody] CreateCommentDto dto)
    {
        // ✅ 纯业务逻辑，无防刷代码
        var userId = User.GetUserId();
        var commentId = await _commentService.CreateCommentAsync(dto, userId);
        return Ok(new { id = commentId, message = "评论发布成功" });
    }
    
    [HttpPost("{postId}/like")]
    [Authorize]
    [AntiSpam(AntiSpamActionType.Like, TargetIdParam = "postId")]
    public async Task<IActionResult> LikePost(long postId)
    {
        // ✅ 纯业务逻辑
        var userId = User.GetUserId();
        await _postLikeService.LikePostAsync(postId, userId);
        return Ok(new { message = "点赞成功" });
    }
    
    [HttpPost("messages")]
    [Authorize]
    [AntiSpam(AntiSpamActionType.Message, TargetIdParam = "dto.ReceiverId")]
    public async Task<IActionResult> SendMessage([FromBody] SendMessageDto dto)
    {
        // ✅ 纯业务逻辑
        var userId = User.GetUserId();
        var messageId = await _messageService.SendMessageAsync(dto, userId);
        return Ok(new { id = messageId });
    }
}
```

## 方案二：MediatR Pipeline Behavior

适用于采用 CQRS 模式的项目，通过 MediatR 管道自动拦截命令。

### 1. 定义防刷请求接口

```csharp
// ZhiCoreCore/Application/Interfaces/IAntiSpamRequest.cs
namespace ZhiCoreCore.Application.Interfaces;

/// <summary>
/// 需要防刷检查的请求接口
/// </summary>
public interface IAntiSpamRequest
{
    /// <summary>
    /// 操作类型
    /// </summary>
    AntiSpamActionType ActionType { get; }
    
    /// <summary>
    /// 用户ID
    /// </summary>
    string UserId { get; }
    
    /// <summary>
    /// 目标ID（可选）
    /// </summary>
    string? TargetId { get; }
    
    /// <summary>
    /// IP地址（可选）
    /// </summary>
    string? IpAddress { get; }
}
```

### 2. 实现 Pipeline Behavior

```csharp
// ZhiCoreCore/Application/Behaviors/AntiSpamBehavior.cs
namespace ZhiCoreCore.Application.Behaviors;

/// <summary>
/// 防刷管道行为 - 自动拦截实现 IAntiSpamRequest 的命令
/// </summary>
public class AntiSpamBehavior<TRequest, TResponse> : IPipelineBehavior<TRequest, TResponse>
    where TRequest : IRequest<TResponse>
{
    private readonly IAntiSpamApplicationService _antiSpamService;
    private readonly ILogger<AntiSpamBehavior<TRequest, TResponse>> _logger;
    
    public AntiSpamBehavior(
        IAntiSpamApplicationService antiSpamService,
        ILogger<AntiSpamBehavior<TRequest, TResponse>> logger)
    {
        _antiSpamService = antiSpamService;
        _logger = logger;
    }
    
    public async Task<TResponse> Handle(
        TRequest request, 
        RequestHandlerDelegate<TResponse> next, 
        CancellationToken cancellationToken)
    {
        // 检查请求是否需要防刷
        if (request is not IAntiSpamRequest antiSpamRequest)
        {
            return await next();
        }
        
        // 执行防刷检查
        var checkResult = await _antiSpamService.CheckActionAsync(
            antiSpamRequest.ActionType,
            antiSpamRequest.UserId,
            antiSpamRequest.TargetId,
            antiSpamRequest.IpAddress);
        
        if (checkResult.IsBlocked)
        {
            _logger.LogWarning(
                "防刷拦截: UserId={UserId}, ActionType={ActionType}, Reason={Reason}",
                antiSpamRequest.UserId, antiSpamRequest.ActionType, checkResult.Reason);
            
            throw new AntiSpamException(checkResult);
        }
        
        // 执行业务逻辑
        var response = await next();
        
        // 记录操作
        await _antiSpamService.RecordActionAsync(
            antiSpamRequest.ActionType,
            antiSpamRequest.UserId,
            antiSpamRequest.TargetId,
            antiSpamRequest.IpAddress);
        
        return response;
    }
}
```

### 3. 命令定义示例

```csharp
// ZhiCoreCore/Application/Comment/Commands/CreateCommentCommand.cs
namespace ZhiCoreCore.Application.Comment.Commands;

public record CreateCommentCommand : IRequest<long>, IAntiSpamRequest
{
    public long PostId { get; init; }
    public string Content { get; init; } = string.Empty;
    public long? ParentCommentId { get; init; }
    
    // IAntiSpamRequest 实现
    public AntiSpamActionType ActionType => AntiSpamActionType.Comment;
    public string UserId { get; init; } = string.Empty;
    public string? TargetId => PostId.ToString();
    public string? IpAddress { get; init; }
}

// Handler 只包含纯业务逻辑
public class CreateCommentCommandHandler : IRequestHandler<CreateCommentCommand, long>
{
    private readonly ICommentRepository _commentRepository;
    private readonly IDomainEventDispatcher _eventDispatcher;
    
    public async Task<long> Handle(CreateCommentCommand request, CancellationToken ct)
    {
        // ✅ 纯业务逻辑，无防刷代码
        var comment = new Comment
        {
            PostId = request.PostId,
            Content = request.Content,
            AuthorId = request.UserId,
            ParentCommentId = request.ParentCommentId
        };
        
        await _commentRepository.AddAsync(comment);
        
        await _eventDispatcher.DispatchAsync(new CommentCreatedEvent
        {
            CommentId = comment.Id,
            PostId = comment.PostId,
            AuthorId = comment.AuthorId
        });
        
        return comment.Id;
    }
}
```

## 方案三：Action Filter（轻量级）

适用于不想引入中间件或 MediatR 的简单场景。

```csharp
// ZhiCoreApi/Filters/AntiSpamActionFilter.cs
namespace ZhiCoreApi.Filters;

/// <summary>
/// 防刷 Action Filter
/// </summary>
public class AntiSpamActionFilter : IAsyncActionFilter
{
    private readonly IAntiSpamApplicationService _antiSpamService;
    private readonly ILogger<AntiSpamActionFilter> _logger;
    
    public AntiSpamActionFilter(
        IAntiSpamApplicationService antiSpamService,
        ILogger<AntiSpamActionFilter> logger)
    {
        _antiSpamService = antiSpamService;
        _logger = logger;
    }
    
    public async Task OnActionExecutionAsync(
        ActionExecutingContext context, 
        ActionExecutionDelegate next)
    {
        var antiSpamAttr = context.ActionDescriptor.EndpointMetadata
            .OfType<AntiSpamAttribute>()
            .FirstOrDefault();
        
        if (antiSpamAttr == null)
        {
            await next();
            return;
        }
        
        var userId = context.HttpContext.User.GetUserId();
        if (string.IsNullOrEmpty(userId))
        {
            await next();
            return;
        }
        
        var targetId = GetTargetId(context, antiSpamAttr);
        var ipAddress = context.HttpContext.Connection.RemoteIpAddress?.ToString();
        
        var checkResult = await _antiSpamService.CheckActionAsync(
            antiSpamAttr.ActionType, userId, targetId, ipAddress);
        
        if (checkResult.IsBlocked)
        {
            context.Result = new ObjectResult(new
            {
                message = checkResult.Reason,
                limitType = checkResult.LimitType?.ToString(),
                cooldownSeconds = checkResult.CooldownSeconds
            })
            {
                StatusCode = StatusCodes.Status429TooManyRequests
            };
            return;
        }
        
        var executedContext = await next();
        
        // 成功后记录
        if (executedContext.Exception == null && antiSpamAttr.RecordOnSuccess)
        {
            await _antiSpamService.RecordActionAsync(
                antiSpamAttr.ActionType, userId, targetId, ipAddress);
        }
    }
    
    private string? GetTargetId(ActionExecutingContext context, AntiSpamAttribute attr)
    {
        if (string.IsNullOrEmpty(attr.TargetIdParam))
            return null;
        
        // 从 Action 参数获取
        if (context.ActionArguments.TryGetValue(attr.TargetIdParam, out var value))
        {
            return value?.ToString();
        }
        
        // 支持嵌套属性（如 dto.PostId）
        var parts = attr.TargetIdParam.Split('.');
        if (parts.Length > 1 && context.ActionArguments.TryGetValue(parts[0], out var obj))
        {
            return GetNestedPropertyValue(obj, parts[1..]);
        }
        
        return null;
    }
    
    private string? GetNestedPropertyValue(object? obj, string[] propertyPath)
    {
        if (obj == null) return null;
        
        var current = obj;
        foreach (var prop in propertyPath)
        {
            var propInfo = current.GetType().GetProperty(prop, 
                BindingFlags.Public | BindingFlags.Instance | BindingFlags.IgnoreCase);
            if (propInfo == null) return null;
            current = propInfo.GetValue(current);
            if (current == null) return null;
        }
        
        return current.ToString();
    }
}
```

## 异常处理

### 定义防刷异常

```csharp
// ZhiCoreCore/Application/Exceptions/AntiSpamException.cs
namespace ZhiCoreCore.Application.Exceptions;

/// <summary>
/// 防刷异常
/// </summary>
public class AntiSpamException : Exception
{
    public AntiSpamCheckResult CheckResult { get; }
    
    public AntiSpamException(AntiSpamCheckResult checkResult)
        : base(checkResult.Reason)
    {
        CheckResult = checkResult;
    }
}
```

### 全局异常处理

```csharp
// ZhiCoreApi/Middlewares/ExceptionHandlingMiddleware.cs
public class ExceptionHandlingMiddleware
{
    public async Task InvokeAsync(HttpContext context, RequestDelegate next)
    {
        try
        {
            await next(context);
        }
        catch (AntiSpamException ex)
        {
            context.Response.StatusCode = StatusCodes.Status429TooManyRequests;
            context.Response.ContentType = "application/json";
            
            if (ex.CheckResult.CooldownSeconds.HasValue)
            {
                context.Response.Headers.RetryAfter = 
                    ex.CheckResult.CooldownSeconds.Value.ToString();
            }
            
            await context.Response.WriteAsJsonAsync(new
            {
                message = ex.Message,
                limitType = ex.CheckResult.LimitType?.ToString(),
                cooldownSeconds = ex.CheckResult.CooldownSeconds
            });
        }
    }
}
```

## DI 注册

```csharp
// ZhiCoreApi/Extensions/AntiSpamExtensions.cs
namespace ZhiCoreApi.Extensions;

public static class AntiSpamExtensions
{
    public static IServiceCollection AddAntiSpamServices(this IServiceCollection services)
    {
        // 注册 Domain Service
        services.AddScoped<IAntiSpamDomainService, AntiSpamDomainService>();
        
        // 注册 Application Service
        services.AddScoped<IAntiSpamApplicationService, AntiSpamApplicationService>();
        
        // 注册 Repository
        services.AddScoped<IUserActionHistoryRepository, UserActionHistoryRepository>();
        
        // 注册 Action Filter（如果使用方案三）
        services.AddScoped<AntiSpamActionFilter>();
        
        return services;
    }
    
    public static IApplicationBuilder UseAntiSpam(this IApplicationBuilder app)
    {
        // 使用中间件（方案一）
        app.UseMiddleware<AntiSpamMiddleware>();
        
        return app;
    }
}

// Program.cs
var builder = WebApplication.CreateBuilder(args);

// 注册服务
builder.Services.AddAntiSpamServices();

// 如果使用 MediatR（方案二）
builder.Services.AddMediatR(cfg =>
{
    cfg.RegisterServicesFromAssembly(typeof(CreateCommentCommand).Assembly);
    cfg.AddBehavior(typeof(IPipelineBehavior<,>), typeof(AntiSpamBehavior<,>));
});

var app = builder.Build();

// 使用中间件
app.UseAntiSpam();
```

## 方案对比

| 特性 | 方案一：中间件 | 方案二：MediatR | 方案三：Filter |
|------|---------------|-----------------|----------------|
| 侵入性 | 低 | 中 | 低 |
| 灵活性 | 高 | 高 | 中 |
| 适用场景 | 所有 HTTP 请求 | CQRS 命令 | Controller Action |
| 实现复杂度 | 中 | 中 | 低 |
| 可测试性 | 高 | 高 | 中 |
| 推荐度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |

## 推荐方案

对于当前项目，推荐采用 **方案一（中间件）+ 方案三（Filter）混合**：

1. **中间件**：处理通用的 HTTP 层防刷逻辑
2. **Filter**：处理需要特殊参数提取的场景
3. **保留 Application Service**：供内部服务调用（如后台任务）

这样可以：
- 最大程度解耦业务代码
- 保持灵活性
- 支持多种调用场景

## 迁移步骤

1. **创建基础设施**
   - 实现 `AntiSpamAttribute`
   - 实现 `AntiSpamMiddleware`
   - 实现 `AntiSpamActionFilter`

2. **重构 Application Service**
   - 移除显式的防刷检查代码
   - 保留 `IAntiSpamApplicationService` 供内部调用

3. **标注 Controller Actions**
   - 为需要防刷的 Action 添加 `[AntiSpam]` 特性

4. **测试验证**
   - 单元测试中间件和 Filter
   - 集成测试完整流程

5. **清理旧代码**
   - 移除业务层中的防刷调用代码
