# DI 装饰器模式注册说明

## 为什么要移除已注册的服务？

在使用装饰器模式时，需要先移除已注册的服务，原因是**避免 DI 容器中出现重复注册**。

## 问题场景

在 `Program.cs` 中，基础服务已经被注册：

```csharp
builder.Services.AddScoped<IUserService, UserService>();
```

当 `AddCacheDecorators` 被调用时，如果不先移除已有的注册，DI 容器中就会有**两个** `IUserService` 的注册：

1. 原来的 `UserService`
2. 新的 `CachedUserService`（装饰器）

## 可能导致的问题

- **默认行为不确定**：DI 容器通常会使用最后注册的实现，但这不是确定性的行为
- **GetServices<T>() 返回多个实例**：如果使用 `GetServices<IUserService>()`，会返回两个实例
- **可能的歧义**：某些 DI 场景下可能导致不可预测的行为

## 正确的注册流程

```csharp
// 1. 移除已有注册
var descriptor = services.FirstOrDefault(d => d.ServiceType == typeof(IUserService));
if (descriptor != null)
{
    services.Remove(descriptor);
}

// 2. 注册内部服务（具体类型，供装饰器依赖）
services.AddScoped<UserService>();

// 3. 注册装饰器作为接口实现
services.AddScoped<IUserService>(sp =>
{
    var inner = sp.GetRequiredService<UserService>();
    var redis = sp.GetRequiredService<IConnectionMultiplexer>();
    // ... 其他依赖
    return new CachedUserService(inner, redis, ...);
});
```

## 注册后的依赖关系

```
调用方 (Controller)
    │
    ▼ 注入 IUserService
┌─────────────────────────┐
│   CachedUserService     │  ← DI 容器返回的是装饰器
│   (实现 IUserService)    │
└─────────────────────────┘
    │
    ▼ 内部依赖 UserService
┌─────────────────────────┐
│      UserService        │  ← 装饰器内部使用的原始服务
│   (具体类型注册)         │
└─────────────────────────┘
```

## 使用 Scrutor 简化

如果项目引入 [Scrutor](https://github.com/khellang/Scrutor) 库，可以更简洁地实现装饰器模式：

```csharp
// Scrutor 会自动处理移除和重新注册的细节
services.Decorate<IUserService, CachedUserService>();
```

Scrutor 内部会：
1. 找到已注册的 `IUserService`
2. 将其替换为装饰器
3. 将原始实现作为装饰器的依赖注入

## 本项目的实现

由于项目没有引入 Scrutor，所以在 `CacheDecoratorServiceExtensions.cs` 中手动实现了这个逻辑：

```csharp
private static IServiceCollection AddUserCacheDecorator(this IServiceCollection services)
{
    // 移除已注册的 IUserService
    var descriptor = services.FirstOrDefault(d => d.ServiceType == typeof(IUserService));
    if (descriptor != null)
    {
        services.Remove(descriptor);
    }
    
    // 注册内部服务
    services.AddScoped<UserService>();
    
    // 注册装饰器
    services.AddScoped<IUserService>(sp =>
    {
        var inner = sp.GetRequiredService<UserService>();
        // ... 构造装饰器
        return new CachedUserService(inner, ...);
    });
    
    return services;
}
```

## 注意事项

1. **调用顺序**：`AddCacheDecorators` 必须在基础服务注册之后调用
2. **生命周期一致**：装饰器和内部服务应使用相同的生命周期（如都是 Scoped）
3. **循环依赖**：装饰器不能直接依赖 `IUserService`，否则会造成循环依赖，必须依赖具体类型 `UserService`
