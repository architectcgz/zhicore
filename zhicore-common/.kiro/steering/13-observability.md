---
inclusion: fileMatch
fileMatchPattern: '**/*.java'
---

# 可观测性与日志

[返回索引](./README-zh.md)

---

## 日志规范

### 日志级别使用

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| **ERROR** | 系统错误、异常 | 数据库连接失败、第三方服务调用失败 |
| **WARN** | 警告信息、降级 | 缓存未命中、限流触发、降级策略启用 |
| **INFO** | 关键业务流程 | 用户登录、订单创建、支付成功 |
| **DEBUG** | 调试信息 | 方法入参、SQL 语句、中间结果 |
| **TRACE** | 详细追踪 | 循环内部、详细调用链 |

### 日志必须包含的信息

```java
// ✅ 正确 - 包含完整上下文
log.info("用户登录成功: userId={}, appId={}, ip={}, traceId={}", 
    userId, appId, ip, MDC.get("traceId"));

// ❌ 错误 - 缺少上下文
log.info("登录成功");
```

**必须包含的字段：**
- **traceId**：链路追踪 ID（分布式追踪）
- **userId/appId**：业务标识（如果有）
- **关键业务参数**：订单号、文章 ID 等（脱敏后）
- **操作结果**：成功/失败、耗时

---

## 日志禁止项

### 禁止大对象打印

```java
// ❌ 错误 - 打印整个对象
log.info("创建文章: {}", post);

// ❌ 错误 - 打印大量数据
log.info("查询结果: {}", postList);

// ✅ 正确 - 只打印关键字段
log.info("创建文章: postId={}, title={}, ownerId={}", 
    post.getId(), post.getTitle(), post.getOwnerId());

// ✅ 正确 - 打印数量而非内容
log.info("查询文章列表: count={}, page={}", postList.size(), page);
```

### 禁止循环打印

```java
// ❌ 错误 - 循环内打印
for (Post post : posts) {
    log.info("处理文章: {}", post.getId());
}

// ✅ 正确 - 批量打印
log.info("批量处理文章: postIds={}, count={}", 
    posts.stream().map(Post::getId).collect(Collectors.toList()),
    posts.size()
);
```

---

## 错误日志规范

**错误日志必须带堆栈信息**

```java
// ❌ 错误 - 丢失堆栈
try {
    // ...
} catch (Exception e) {
    log.error("操作失败: {}", e.getMessage());
}

// ✅ 正确 - 保留堆栈
try {
    // ...
} catch (Exception e) {
    log.error("操作失败: userId={}, operation={}", userId, operation, e);
}
```

---

## 链路追踪

### MDC 配置

```java
@Component
public class TraceIdFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### Logback 配置

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
</configuration>
```

---

## 监控指标

### 必须监控的指标

| 指标类型 | 指标名称 | 说明 |
|---------|---------|------|
| **请求指标** | QPS、响应时间、错误率 | 接口性能监控 |
| **业务指标** | 注册量、登录量、订单量 | 业务健康度 |
| **资源指标** | CPU、内存、磁盘、网络 | 系统资源使用 |
| **依赖指标** | 数据库连接、Redis 连接、MQ 连接 | 依赖服务健康 |

### Micrometer 集成

```java
@RestController
public class UserController {
    
    private final MeterRegistry meterRegistry;
    
    @PostMapping("/register")
    public Result register(@RequestBody RegisterReq req) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 业务逻辑
            meterRegistry.counter("user.register.success").increment();
            return Result.success();
        } catch (Exception e) {
            meterRegistry.counter("user.register.failure").increment();
            throw e;
        } finally {
            sample.stop(Timer.builder("user.register.time")
                .tag("status", "success")
                .register(meterRegistry));
        }
    }
}
```

---

**最后更新**：2026-02-01
