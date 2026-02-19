# 服务降级

## 概述

服务降级是系统弹性的重要组成部分，当主服务不可用时，自动切换到备用方案，保证系统可用性。在 DDD 架构中，降级逻辑属于基础设施层。

## 降级架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     Application Layer                            │
│         - 不感知降级逻辑                                          │
│         - 通过接口调用，自动降级                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                           │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              Resilience Decorator / Policy                   ││
│  │                                                              ││
│  │  - Circuit Breaker（熔断器）                                 ││
│  │  - Retry（重试）                                             ││
│  │  - Fallback（降级）                                          ││
│  │  - Timeout（超时）                                           ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────┐    ┌─────────────────────┐             │
│  │   Primary Service   │    │  Fallback Service   │             │
│  │   (主服务)          │    │  (降级服务)         │             │
│  └─────────────────────┘    └─────────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

## Polly 策略

### 基础策略工具类

```csharp
// BlogCore/Infrastructure/Resilience/ResiliencePolicies.cs
public static class ResiliencePolicies
{
    /// <summary>
    /// 创建带降级的弹性策略
    /// </summary>
    public static AsyncPolicyWrap<T> CreateWithFallback<T>(
        Func<Context, CancellationToken, Task<T>> fallbackAction,
        ILogger logger,
        string serviceName = "Service")
    {
        // 1. 重试策略（指数退避）
        var retryPolicy = Policy<T>
            .Handle<Exception>()
            .WaitAndRetryAsync(
                retryCount: 3,
                sleepDurationProvider: attempt => TimeSpan.FromMilliseconds(100 * Math.Pow(2, attempt)),
                onRetry: (outcome, timespan, attempt, context) =>
                {
                    logger.LogWarning(
                        "{ServiceName} 重试第 {Attempt} 次，等待 {Timespan}ms，原因: {Exception}",
                        serviceName, attempt, timespan.TotalMilliseconds, outcome.Exception?.Message);
                });
        
        // 2. 熔断策略
        var circuitBreakerPolicy = Policy<T>
            .Handle<Exception>()
            .CircuitBreakerAsync(
                handledEventsAllowedBeforeBreaking: 5,
                durationOfBreak: TimeSpan.FromSeconds(30),
                onBreak: (outcome, breakDelay) =>
                {
                    logger.LogWarning(
                        "{ServiceName} 熔断器打开，持续 {BreakDelay}s，原因: {Exception}",
                        serviceName, breakDelay.TotalSeconds, outcome.Exception?.Message);
                },
                onReset: () =>
                {
                    logger.LogInformation("{ServiceName} 熔断器重置", serviceName);
                },
                onHalfOpen: () =>
                {
                    logger.LogInformation("{ServiceName} 熔断器半开", serviceName);
                });
        
        // 3. 降级策略
        var fallbackPolicy = Policy<T>
            .Handle<Exception>()
            .Or<BrokenCircuitException>()
            .FallbackAsync(
                fallbackAction: fallbackAction,
                onFallbackAsync: (outcome, context) =>
                {
                    logger.LogWarning(
                        "{ServiceName} 服务降级: {Exception}",
                        serviceName, outcome.Exception?.Message);
                    return Task.CompletedTask;
                });
        
        // 组合策略：降级 -> 熔断 -> 重试
        return fallbackPolicy.WrapAsync(circuitBreakerPolicy).WrapAsync(retryPolicy);
    }
    
    /// <summary>
    /// 创建超时策略
    /// </summary>
    public static AsyncTimeoutPolicy CreateTimeoutPolicy(
        TimeSpan timeout,
        ILogger logger,
        string serviceName = "Service")
    {
        return Policy.TimeoutAsync(
            timeout,
            TimeoutStrategy.Pessimistic,
            onTimeoutAsync: (context, timespan, task) =>
            {
                logger.LogWarning("{ServiceName} 操作超时: {Timeout}s", serviceName, timespan.TotalSeconds);
                return Task.CompletedTask;
            });
    }
}
```

### Redis 策略提供者

```csharp
// BlogCore/Services/Resilience/IRedisPolicyProvider.cs
public interface IRedisPolicyProvider
{
    /// <summary>
    /// 执行带降级的 Redis 操作
    /// </summary>
    Task<T> ExecuteWithFallbackAsync<T>(
        Func<Context, Task<T>> action,
        Func<Context, Task<T>> fallbackAction,
        string operationKey);
    
    /// <summary>
    /// 静默执行 Redis 操作（失败不抛异常）
    /// </summary>
    Task ExecuteSilentAsync(
        Func<Context, Task> action,
        string operationKey);
}

// BlogCore/Services/Resilience/RedisPolicyProvider.cs
public class RedisPolicyProvider : IRedisPolicyProvider
{
    private readonly AsyncPolicyWrap _policy;
    private readonly ILogger<RedisPolicyProvider> _logger;
    
    public RedisPolicyProvider(ILogger<RedisPolicyProvider> logger)
    {
        _logger = logger;
        
        // 重试策略
        var retryPolicy = Policy
            .Handle<RedisConnectionException>()
            .Or<RedisTimeoutException>()
            .WaitAndRetryAsync(
                retryCount: 2,
                sleepDurationProvider: _ => TimeSpan.FromMilliseconds(100));
        
        // 熔断策略
        var circuitBreakerPolicy = Policy
            .Handle<RedisConnectionException>()
            .Or<RedisTimeoutException>()
            .CircuitBreakerAsync(
                handledEventsAllowedBeforeBreaking: 5,
                durationOfBreak: TimeSpan.FromSeconds(30));
        
        _policy = Policy.WrapAsync(circuitBreakerPolicy, retryPolicy);
    }
    
    public async Task<T> ExecuteWithFallbackAsync<T>(
        Func<Context, Task<T>> action,
        Func<Context, Task<T>> fallbackAction,
        string operationKey)
    {
        var context = new Context(operationKey);
        
        try
        {
            return await _policy.ExecuteAsync(action, context);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Redis 操作失败，执行降级: {OperationKey}", operationKey);
            return await fallbackAction(context);
        }
    }
    
    public async Task ExecuteSilentAsync(
        Func<Context, Task> action,
        string operationKey)
    {
        var context = new Context(operationKey);
        
        try
        {
            await _policy.ExecuteAsync(async ctx =>
            {
                await action(ctx);
                return true;
            }, context);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Redis 静默操作失败: {OperationKey}", operationKey);
            // 静默失败，不抛异常
        }
    }
}
```

### RabbitMQ 策略提供者

```csharp
// BlogCore/Services/Resilience/IRabbitMqPolicyProvider.cs
public interface IRabbitMqPolicyProvider
{
    /// <summary>
    /// 执行带降级的 MQ 操作
    /// </summary>
    Task ExecuteWithFallbackAsync(
        Func<Context, Task> action,
        Func<Context, Task> fallbackAction,
        string operationKey);
    
    /// <summary>
    /// 静默执行 MQ 操作
    /// </summary>
    Task ExecuteSilentAsync(
        Func<Context, Task> action,
        string operationKey);
}

// BlogCore/Services/Resilience/RabbitMqPolicyProvider.cs
public class RabbitMqPolicyProvider : IRabbitMqPolicyProvider
{
    private readonly AsyncCircuitBreakerPolicy _circuitBreaker;
    private readonly ILogger<RabbitMqPolicyProvider> _logger;
    
    public RabbitMqPolicyProvider(ILogger<RabbitMqPolicyProvider> logger)
    {
        _logger = logger;
        
        _circuitBreaker = Policy
            .Handle<RabbitMQ.Client.Exceptions.BrokerUnreachableException>()
            .Or<RabbitMQ.Client.Exceptions.AlreadyClosedException>()
            .CircuitBreakerAsync(
                handledEventsAllowedBeforeBreaking: 3,
                durationOfBreak: TimeSpan.FromSeconds(60),
                onBreak: (ex, breakDelay) =>
                {
                    logger.LogWarning("RabbitMQ 熔断器打开，持续 {BreakDelay}s", breakDelay.TotalSeconds);
                },
                onReset: () =>
                {
                    logger.LogInformation("RabbitMQ 熔断器重置");
                });
    }
    
    public async Task ExecuteWithFallbackAsync(
        Func<Context, Task> action,
        Func<Context, Task> fallbackAction,
        string operationKey)
    {
        var context = new Context(operationKey);
        
        try
        {
            await _circuitBreaker.ExecuteAsync(action, context);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "RabbitMQ 操作失败，执行降级: {OperationKey}", operationKey);
            await fallbackAction(context);
        }
    }
}
```

## 降级服务实现

### 搜索服务降级

```csharp
// BlogCore/Infrastructure/Resilience/ResilientSearchService.cs
/// <summary>
/// 带降级能力的搜索服务
/// 主服务：Elasticsearch
/// 降级服务：Database
/// </summary>
public class ResilientSearchService : ISearchService
{
    private readonly IElasticsearchService _primaryService;
    private readonly IDatabaseSearchService _fallbackService;
    private readonly AsyncPolicyWrap<SearchResult> _policy;
    private readonly ILogger<ResilientSearchService> _logger;
    
    public ResilientSearchService(
        IElasticsearchService primaryService,
        IDatabaseSearchService fallbackService,
        ILogger<ResilientSearchService> logger)
    {
        _primaryService = primaryService;
        _fallbackService = fallbackService;
        _logger = logger;
        
        _policy = ResiliencePolicies.CreateWithFallback<SearchResult>(
            fallbackAction: async (context, ct) =>
            {
                _logger.LogWarning("Elasticsearch 不可用，降级到数据库搜索");
                var query = context["query"] as string;
                return await _fallbackService.SearchAsync(query!, ct);
            },
            logger,
            "Elasticsearch");
    }
    
    public async Task<SearchResult> SearchAsync(string query, CancellationToken ct = default)
    {
        var context = new Context { ["query"] = query };
        
        return await _policy.ExecuteAsync(
            async (ctx, token) => await _primaryService.SearchAsync(query, token),
            context,
            ct);
    }
}
```

### 事件发布器降级

```csharp
// BlogCore/Infrastructure/Resilience/ResilientEventPublisher.cs
/// <summary>
/// 带降级能力的事件发布器
/// 主服务：RabbitMQ
/// 降级服务：进程内分发
/// </summary>
public class ResilientEventPublisher : IEventPublisher
{
    private readonly RabbitMqEventPublisher _primaryPublisher;
    private readonly InProcessDomainEventDispatcher _fallbackDispatcher;
    private readonly IRabbitMqPolicyProvider _policyProvider;
    private readonly ILogger<ResilientEventPublisher> _logger;
    
    public ResilientEventPublisher(
        RabbitMqEventPublisher primaryPublisher,
        InProcessDomainEventDispatcher fallbackDispatcher,
        IRabbitMqPolicyProvider policyProvider,
        ILogger<ResilientEventPublisher> logger)
    {
        _primaryPublisher = primaryPublisher;
        _fallbackDispatcher = fallbackDispatcher;
        _policyProvider = policyProvider;
        _logger = logger;
    }
    
    public async Task PublishAsync<T>(T message, CancellationToken ct = default) where T : class
    {
        await _policyProvider.ExecuteWithFallbackAsync(
            async _ =>
            {
                await _primaryPublisher.PublishAsync(message, ct);
                _logger.LogDebug("消息已发布到 RabbitMQ: {MessageType}", typeof(T).Name);
            },
            async _ =>
            {
                _logger.LogWarning("RabbitMQ 不可用，降级到进程内分发: {MessageType}", typeof(T).Name);
                
                if (message is IDomainEvent domainEvent)
                {
                    await _fallbackDispatcher.DispatchAsync(domainEvent, ct);
                }
            },
            $"EventPublisher:Publish:{typeof(T).Name}");
    }
}
```

## 降级场景清单

| 服务 | 主服务 | 降级服务 | 触发条件 | 影响 |
|------|--------|---------|---------|------|
| 搜索 | Elasticsearch | Database | ES 连接失败或超时 | 搜索性能下降 |
| 事件发布 | RabbitMQ | InProcess | MQ 连接失败 | 异步变同步 |
| 缓存读取 | Redis | Database | Redis 连接失败 | 响应变慢 |
| 缓存写入 | Redis | 跳过 | Redis 连接失败 | 数据不一致风险 |
| 热度计算 | TieredHotness | LazyHotness | 计算超时 | 热度不精确 |
| 通知推送 | SignalR | 数据库存储 | WebSocket 断开 | 实时性下降 |
| 阅读量统计 | Redis | 跳过 | Redis 连接失败 | 统计不准确 |

## DI 注册

```csharp
// BlogCore/Extensions/ResilienceServiceExtensions.cs
public static class ResilienceServiceExtensions
{
    public static IServiceCollection AddResilienceServices(this IServiceCollection services)
    {
        // 注册策略提供者
        services.AddSingleton<IRedisPolicyProvider, RedisPolicyProvider>();
        services.AddSingleton<IRabbitMqPolicyProvider, RabbitMqPolicyProvider>();
        
        // 注册主服务
        services.AddScoped<IElasticsearchService, ElasticsearchService>();
        services.AddScoped<IDatabaseSearchService, DatabaseSearchService>();
        services.AddScoped<RabbitMqEventPublisher>();
        services.AddScoped<InProcessDomainEventDispatcher>();
        
        // 注册带降级能力的服务
        services.AddScoped<ISearchService, ResilientSearchService>();
        
        // 注册带降级能力的事件发布器
        services.AddScoped<IEventPublisher>(sp =>
        {
            var primary = sp.GetRequiredService<RabbitMqEventPublisher>();
            var fallback = sp.GetRequiredService<InProcessDomainEventDispatcher>();
            var policy = sp.GetRequiredService<IRabbitMqPolicyProvider>();
            var logger = sp.GetRequiredService<ILogger<ResilientEventPublisher>>();
            return new ResilientEventPublisher(primary, fallback, policy, logger);
        });
        
        return services;
    }
}
```

## 监控与告警

### 熔断器状态监控

```csharp
// BlogCore/Services/Background/CircuitBreakerMonitorService.cs
public class CircuitBreakerMonitorService : BackgroundService
{
    private readonly IRedisPolicyProvider _redisPolicyProvider;
    private readonly IRabbitMqPolicyProvider _rabbitMqPolicyProvider;
    private readonly ILogger<CircuitBreakerMonitorService> _logger;
    
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            // 检查熔断器状态
            // 发送告警（如果熔断器打开）
            await Task.Delay(TimeSpan.FromSeconds(30), stoppingToken);
        }
    }
}
```

### 降级指标收集

```csharp
// 使用 Prometheus 或其他监控系统收集指标
public class ResilienceMetrics
{
    private static readonly Counter FallbackCounter = Metrics.CreateCounter(
        "service_fallback_total",
        "Total number of fallback executions",
        new CounterConfiguration
        {
            LabelNames = new[] { "service", "operation" }
        });
    
    public static void RecordFallback(string service, string operation)
    {
        FallbackCounter.WithLabels(service, operation).Inc();
    }
}
```
