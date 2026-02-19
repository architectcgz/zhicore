# 架构增强设计

本文档定义了应用于整个微服务系统的架构增强方案，所有服务都应遵循这些设计规范。

## 增强方案应用矩阵

| 增强方案 | User | Post | Comment | Message | Notification | Search | Ranking | Upload | Admin |
|---------|------|------|---------|---------|--------------|--------|---------|--------|-------|
| API版本控制 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Saga事务 | - | ✅ | - | - | - | - | - | - | - |
| Gateway限流 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Sentinel限流 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 监控指标 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| RBAC权限 | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | ✅ | ✅ |
| DataLoader | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | ✅ |
| 读写分离 | ✅ | ✅ | ✅ | ✅ | ✅ | - | - | - | - |

---

## 1. API 版本控制策略

### 1.1 版本控制规范

所有服务统一使用 URL 路径版本控制：
- 当前版本：`/api/v1/*`
- 新版本：`/api/v2/*`（当有破坏性变更时）

### 1.2 公共模块实现

```java
// common-web 模块
package com.blog.common.web.version;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiVersion {
    int value() default 1;
}

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {
    
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
            "api/v{version}",
            HandlerTypePredicate.forAnnotation(RestController.class)
                .and(HandlerTypePredicate.forAnnotation(ApiVersion.class))
        );
    }
}
```


### 1.3 各服务应用示例

```java
// Post Service
@RestController
@RequestMapping("/posts")
@ApiVersion(1)
public class PostControllerV1 {
    
    @GetMapping("/{postId}")
    public Result<PostVO> getPost(@PathVariable Long postId) { ... }
}

// V2 版本（当需要破坏性变更时）
@RestController
@RequestMapping("/posts")
@ApiVersion(2)
public class PostControllerV2 {
    
    @GetMapping("/{postId}")
    public Result<PostDetailVO> getPost(@PathVariable Long postId) { ... }
}
```

### 1.4 Gateway 版本路由

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Post Service - V1
        - id: post-service-v1
          uri: lb://post-service
          predicates:
            - Path=/api/v1/posts/**
          filters:
            - StripPrefix=2
            
        # Post Service - V2
        - id: post-service-v2
          uri: lb://post-service
          predicates:
            - Path=/api/v2/posts/**
          filters:
            - StripPrefix=2
            
        # Comment Service
        - id: comment-service-v1
          uri: lb://comment-service
          predicates:
            - Path=/api/v1/comments/**
          filters:
            - StripPrefix=2
            
        # User Service
        - id: user-service-v1
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**, /api/v1/auth/**
          filters:
            - StripPrefix=2
```

---

## 2. Saga 分布式事务

### 2.1 适用场景

仅在需要跨服务强一致性的场景使用 Saga：
- **发布文章**：Post → Search → Ranking → Notification
- **删除用户**：User → Post → Comment → Message → Notification

### 2.2 Saga 编排器基类

```java
// common-saga 模块
package com.blog.common.saga;

public abstract class AbstractSaga<T> {
    
    protected final SagaLogRepository sagaLogRepository;
    protected final List<SagaStep<T>> steps = new ArrayList<>();
    
    public void execute(T context) {
        String sagaId = UUID.randomUUID().toString();
        SagaLog sagaLog = SagaLog.create(sagaId, getSagaName(), getResourceId(context));
        
        try {
            for (SagaStep<T> step : steps) {
                sagaLog.addStep(step.getName(), SagaStepStatus.PENDING);
                step.execute(context);
                sagaLog.updateStep(step.getName(), SagaStepStatus.COMPLETED);
            }
            sagaLog.complete();
        } catch (Exception e) {
            sagaLog.fail(e.getMessage());
            compensate(sagaLog, context);
            throw new SagaException(getSagaName() + " 执行失败", e);
        } finally {
            sagaLogRepository.save(sagaLog);
        }
    }
    
    private void compensate(SagaLog sagaLog, T context) {
        List<SagaStep<T>> completedSteps = getCompletedSteps(sagaLog);
        Collections.reverse(completedSteps);
        
        for (SagaStep<T> step : completedSteps) {
            try {
                step.compensate(context);
                sagaLog.updateStep(step.getName(), SagaStepStatus.COMPENSATED);
            } catch (Exception e) {
                sagaLog.updateStep(step.getName(), SagaStepStatus.COMPENSATION_FAILED);
                log.error("补偿失败: {}", step.getName(), e);
            }
        }
    }
    
    protected abstract String getSagaName();
    protected abstract String getResourceId(T context);
}

public interface SagaStep<T> {
    String getName();
    void execute(T context);
    void compensate(T context);
}
```

### 2.3 发布文章 Saga 实现

```java
@Service
public class PublishPostSaga extends AbstractSaga<PublishPostContext> {
    
    public PublishPostSaga(SagaLogRepository sagaLogRepository,
                          PostService postService,
                          SearchServiceClient searchService,
                          RankingServiceClient rankingService,
                          NotificationServiceClient notificationService) {
        super(sagaLogRepository);
        
        // 定义步骤
        steps.add(new PublishPostStep(postService));
        steps.add(new IndexPostStep(searchService));
        steps.add(new UpdateRankingStep(rankingService));
        steps.add(new NotifyFollowersStep(notificationService));
    }
    
    @Override
    protected String getSagaName() {
        return "PUBLISH_POST";
    }
    
    @Override
    protected String getResourceId(PublishPostContext context) {
        return context.getPostId().toString();
    }
}

@Data
public class PublishPostContext {
    private Long postId;
    private String authorId;
    private String title;
}

// 步骤实现
public class IndexPostStep implements SagaStep<PublishPostContext> {
    
    private final SearchServiceClient searchService;
    
    @Override
    public String getName() { return "INDEX_POST"; }
    
    @Override
    public void execute(PublishPostContext context) {
        searchService.indexPost(context.getPostId());
    }
    
    @Override
    public void compensate(PublishPostContext context) {
        searchService.deletePostIndex(context.getPostId());
    }
}
```


---

## 3. 统一限流策略

### 3.1 限流层次

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              限流架构                                            │
│                                                                                  │
│   Layer 1: Gateway 限流（全局入口）                                               │
│   ├── 基于用户 ID 限流（登录用户）                                                │
│   ├── 基于 IP 限流（匿名用户）                                                    │
│   └── 基于 API 路径限流（热点接口）                                               │
│                                                                                  │
│   Layer 2: Sentinel 服务限流（服务内部）                                          │
│   ├── 接口级别限流                                                               │
│   ├── 热点参数限流                                                               │
│   └── 系统自适应限流                                                             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Gateway 限流配置

```java
// gateway-service
@Configuration
public class RateLimitConfig {
    
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress().getHostString();
            return Mono.just("ip:" + ip);
        };
    }
    
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getPath().value());
    }
}
```

```yaml
# gateway application.yml
spring:
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 50
            redis-rate-limiter.burstCapacity: 100
            key-resolver: "#{@userKeyResolver}"
      routes:
        # 搜索接口 - 高频访问，单独限流
        - id: search-service
          uri: lb://search-service
          predicates:
            - Path=/api/v1/search/**
          filters:
            - StripPrefix=2
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@userKeyResolver}"
                
        # 文章创建 - 写操作，严格限流
        - id: post-create
          uri: lb://post-service
          predicates:
            - Path=/api/v1/posts
            - Method=POST
          filters:
            - StripPrefix=2
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                key-resolver: "#{@userKeyResolver}"
```

### 3.3 Sentinel 服务限流

```java
// common-sentinel 模块
@Configuration
public class SentinelConfig {
    
    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        // 通用读接口限流
        rules.add(createFlowRule("getById", 1000, RuleConstant.CONTROL_BEHAVIOR_WARM_UP));
        rules.add(createFlowRule("list", 500, RuleConstant.CONTROL_BEHAVIOR_WARM_UP));
        
        // 写接口限流
        rules.add(createFlowRule("create", 100, RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER));
        rules.add(createFlowRule("update", 100, RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER));
        rules.add(createFlowRule("delete", 50, RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER));
        
        FlowRuleManager.loadRules(rules);
    }
    
    private FlowRule createFlowRule(String resource, int count, int behavior) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        rule.setControlBehavior(behavior);
        if (behavior == RuleConstant.CONTROL_BEHAVIOR_WARM_UP) {
            rule.setWarmUpPeriodSec(10);
        } else if (behavior == RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER) {
            rule.setMaxQueueingTimeMs(500);
        }
        return rule;
    }
}

// 各服务自定义限流规则
// post-service
@Configuration
public class PostSentinelConfig {
    
    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        // 发布文章 - 严格限流
        rules.add(new FlowRule("publishPost")
            .setGrade(RuleConstant.FLOW_GRADE_QPS)
            .setCount(50));
            
        // 热点参数限流 - 按文章ID限流
        ParamFlowRule hotRule = new ParamFlowRule("getPostById")
            .setParamIdx(0)
            .setGrade(RuleConstant.FLOW_GRADE_QPS)
            .setCount(100);
        ParamFlowRuleManager.loadRules(Collections.singletonList(hotRule));
        
        FlowRuleManager.loadRules(rules);
    }
}
```

### 3.4 限流响应处理

```java
// common-web 模块
@Component
public class GlobalBlockExceptionHandler implements BlockExceptionHandler {
    
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, 
                       BlockException e) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        
        Result<?> result;
        if (e instanceof FlowException) {
            result = Result.error("RATE_LIMITED", "请求过于频繁，请稍后再试");
        } else if (e instanceof DegradeException) {
            result = Result.error("SERVICE_DEGRADED", "服务暂时不可用");
        } else if (e instanceof ParamFlowException) {
            result = Result.error("PARAM_FLOW_LIMITED", "热点参数限流");
        } else if (e instanceof SystemBlockException) {
            result = Result.error("SYSTEM_BLOCKED", "系统负载过高");
        } else {
            result = Result.error("BLOCKED", "请求被拒绝");
        }
        
        response.getWriter().write(JsonUtils.toJson(result));
    }
}
```


---

## 4. 监控指标与告警

### 4.1 公共指标模块

```java
// common-metrics 模块
package com.blog.common.metrics;

@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name}") String appName) {
        return registry -> registry.config()
            .commonTags("application", appName)
            .commonTags("env", System.getenv().getOrDefault("ENV", "dev"));
    }
}

// 业务指标基类
public abstract class AbstractBusinessMetrics {
    
    protected final MeterRegistry registry;
    
    protected Counter createCounter(String name, String description) {
        return Counter.builder(name)
            .description(description)
            .register(registry);
    }
    
    protected Timer createTimer(String name, String description) {
        return Timer.builder(name)
            .description(description)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
    
    protected Gauge createGauge(String name, String description, Supplier<Number> supplier) {
        return Gauge.builder(name, supplier)
            .description(description)
            .register(registry);
    }
}
```

### 4.2 各服务业务指标

```java
// Post Service 指标
@Component
public class PostMetrics extends AbstractBusinessMetrics {
    
    private final Counter postCreatedCounter;
    private final Counter postPublishedCounter;
    private final Counter postLikedCounter;
    private final Timer postQueryTimer;
    private final AtomicLong activePostsGauge;
    
    public PostMetrics(MeterRegistry registry) {
        super(registry);
        
        this.postCreatedCounter = createCounter("blog.posts.created.total", "文章创建总数");
        this.postPublishedCounter = createCounter("blog.posts.published.total", "文章发布总数");
        this.postLikedCounter = createCounter("blog.posts.liked.total", "文章点赞总数");
        this.postQueryTimer = createTimer("blog.posts.query.duration", "文章查询耗时");
        
        this.activePostsGauge = new AtomicLong(0);
        createGauge("blog.posts.active.count", "活跃文章数", activePostsGauge::get);
    }
    
    public void recordPostCreated() { postCreatedCounter.increment(); }
    public void recordPostPublished() { postPublishedCounter.increment(); }
    public void recordPostLiked() { postLikedCounter.increment(); }
    public Timer.Sample startQueryTimer() { return Timer.start(registry); }
    public void stopQueryTimer(Timer.Sample sample) { sample.stop(postQueryTimer); }
    public void setActivePostsCount(long count) { activePostsGauge.set(count); }
}

// Comment Service 指标
@Component
public class CommentMetrics extends AbstractBusinessMetrics {
    
    private final Counter commentCreatedCounter;
    private final Counter commentLikedCounter;
    private final Timer commentQueryTimer;
    
    public CommentMetrics(MeterRegistry registry) {
        super(registry);
        this.commentCreatedCounter = createCounter("blog.comments.created.total", "评论创建总数");
        this.commentLikedCounter = createCounter("blog.comments.liked.total", "评论点赞总数");
        this.commentQueryTimer = createTimer("blog.comments.query.duration", "评论查询耗时");
    }
}

// User Service 指标
@Component
public class UserMetrics extends AbstractBusinessMetrics {
    
    private final Counter userRegisteredCounter;
    private final Counter userLoginCounter;
    private final Counter userFollowedCounter;
    private final Timer authTimer;
    
    public UserMetrics(MeterRegistry registry) {
        super(registry);
        this.userRegisteredCounter = createCounter("blog.users.registered.total", "用户注册总数");
        this.userLoginCounter = createCounter("blog.users.login.total", "用户登录总数");
        this.userFollowedCounter = createCounter("blog.users.followed.total", "关注操作总数");
        this.authTimer = createTimer("blog.auth.duration", "认证耗时");
    }
}
```

### 4.3 Prometheus 告警规则

```yaml
# prometheus/rules/blog-alerts.yml
groups:
  - name: blog-service-alerts
    rules:
      # 服务可用性
      - alert: ServiceDown
        expr: up{job=~".*-service"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.instance }} 不可用"
          description: "{{ $labels.job }} 服务已停止响应超过1分钟"
          
      # 高错误率
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
          / sum(rate(http_server_requests_seconds_count[5m])) by (application) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.application }} 错误率超过 5%"
          
      # 高延迟
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95, 
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application)
          ) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.application }} P95 延迟超过 1 秒"
          
      # Redis 连接池
      - alert: RedisConnectionPoolExhausted
        expr: redis_pool_active / redis_pool_max > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redis 连接池使用率超过 90%"
          
      # 数据库连接池
      - alert: DBConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "数据库连接池使用率超过 90%"
          
      # 消息队列积压
      - alert: RabbitMQQueueBacklog
        expr: rabbitmq_queue_messages > 10000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "RabbitMQ 队列 {{ $labels.queue }} 积压超过 10000 条"
```


---

## 5. RBAC 权限模型

### 5.1 权限模型

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              RBAC 权限模型                                       │
│                                                                                  │
│   角色定义：                                                                      │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ ROLE_USER      │ 普通用户 - 浏览、评论、点赞                              │   │
│   │ ROLE_AUTHOR    │ 作者 - 发布文章、管理自己的内容                          │   │
│   │ ROLE_MODERATOR │ 版主 - 管理评论、举报处理                                │   │
│   │ ROLE_ADMIN     │ 管理员 - 全部权限                                        │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   权限定义：                                                                      │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ 文章: post:create, post:update, post:delete, post:publish               │   │
│   │ 评论: comment:create, comment:delete, comment:manage                    │   │
│   │ 用户: user:view, user:update, user:ban, user:manage                     │   │
│   │ 管理: admin:access, admin:dashboard, admin:settings                     │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 权限注解实现

```java
// common-security 模块
package com.blog.common.security;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String[] value();
    Logical logical() default Logical.AND;
}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String[] value();
    Logical logical() default Logical.OR;
}

/**
 * 资源级权限注解
 * 用于检查用户是否有权限操作特定资源
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireResourcePermission {
    /**
     * 权限标识
     */
    String value();
    
    /**
     * 资源类型（如 post, comment）
     */
    String resourceType();
    
    /**
     * 资源 ID 的 SpEL 表达式（如 #postId）
     */
    String resourceId();
}

public enum Logical { AND, OR }

@Aspect
@Component
@Order(1)
public class PermissionAspect {
    
    private final PermissionService permissionService;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    
    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, 
                                  RequirePermission requirePermission) throws Throwable {
        String userId = SecurityContextHolder.getUserId();
        String[] permissions = requirePermission.value();
        Logical logical = requirePermission.logical();
        
        boolean hasPermission = logical == Logical.AND
            ? permissionService.hasAllPermissions(userId, permissions)
            : permissionService.hasAnyPermission(userId, permissions);
        
        if (!hasPermission) {
            throw new ForbiddenException("权限不足");
        }
        
        return joinPoint.proceed();
    }
    
    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, 
                           RequireRole requireRole) throws Throwable {
        String userId = SecurityContextHolder.getUserId();
        String[] roles = requireRole.value();
        Logical logical = requireRole.logical();
        
        boolean hasRole = logical == Logical.AND
            ? permissionService.hasAllRoles(userId, roles)
            : permissionService.hasAnyRole(userId, roles);
        
        if (!hasRole) {
            throw new ForbiddenException("角色权限不足");
        }
        
        return joinPoint.proceed();
    }
    
    /**
     * 资源级权限检查
     */
    @Around("@annotation(requireResourcePermission)")
    public Object checkResourcePermission(ProceedingJoinPoint joinPoint,
                                          RequireResourcePermission requireResourcePermission) throws Throwable {
        String userId = SecurityContextHolder.getUserId();
        String permission = requireResourcePermission.value();
        String resourceType = requireResourcePermission.resourceType();
        
        // 解析资源 ID
        Object resourceId = resolveResourceId(joinPoint, requireResourcePermission.resourceId());
        
        // 检查资源级权限
        boolean hasPermission = permissionService.checkResourcePermission(
            userId, permission, resourceType, resourceId.toString()
        );
        
        if (!hasPermission) {
            throw new ForbiddenException("无权操作此资源");
        }
        
        return joinPoint.proceed();
    }
    
    private Object resolveResourceId(ProceedingJoinPoint joinPoint, String expression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        
        Expression exp = parser.parseExpression(expression);
        return exp.getValue(context);
    }
}

/**
 * 权限服务
 */
@Service
public class PermissionService {
    
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ResourceOwnershipService resourceOwnershipService;
    
    /**
     * 检查资源级权限
     * 
     * 规则：
     * 1. 管理员拥有所有资源的权限
     * 2. 资源所有者拥有自己资源的权限
     * 3. 其他用户需要检查角色权限
     */
    public boolean checkResourcePermission(String userId, String permission, 
                                           String resourceType, String resourceId) {
        // 1. 检查是否是管理员
        if (hasRole(userId, "ROLE_ADMIN")) {
            return true;
        }
        
        // 2. 检查是否是资源所有者
        if (resourceOwnershipService.isOwner(userId, resourceType, resourceId)) {
            return true;
        }
        
        // 3. 检查角色权限（如版主可以删除任何评论）
        return hasPermission(userId, permission);
    }
    
    public boolean hasPermission(String userId, String permission) {
        Set<String> userPermissions = getUserPermissions(userId);
        return userPermissions.contains(permission);
    }
    
    public boolean hasAllPermissions(String userId, String[] permissions) {
        Set<String> userPermissions = getUserPermissions(userId);
        return Arrays.stream(permissions).allMatch(userPermissions::contains);
    }
    
    public boolean hasAnyPermission(String userId, String[] permissions) {
        Set<String> userPermissions = getUserPermissions(userId);
        return Arrays.stream(permissions).anyMatch(userPermissions::contains);
    }
    
    public boolean hasRole(String userId, String role) {
        return getUserRoles(userId).contains(role);
    }
    
    public boolean hasAllRoles(String userId, String[] roles) {
        Set<String> userRoles = getUserRoles(userId);
        return Arrays.stream(roles).allMatch(userRoles::contains);
    }
    
    public boolean hasAnyRole(String userId, String[] roles) {
        Set<String> userRoles = getUserRoles(userId);
        return Arrays.stream(roles).anyMatch(userRoles::contains);
    }
    
    @Cacheable(value = "user:permissions", key = "#userId")
    private Set<String> getUserPermissions(String userId) {
        List<String> roles = userRoleRepository.findRolesByUserId(userId);
        return rolePermissionRepository.findPermissionsByRoles(roles);
    }
    
    @Cacheable(value = "user:roles", key = "#userId")
    private Set<String> getUserRoles(String userId) {
        return new HashSet<>(userRoleRepository.findRolesByUserId(userId));
    }
}

/**
 * 资源所有权服务
 */
@Service
public class ResourceOwnershipService {
    
    private final Map<String, ResourceOwnerChecker> checkers;
    
    public ResourceOwnershipService(List<ResourceOwnerChecker> checkerList) {
        this.checkers = checkerList.stream()
            .collect(Collectors.toMap(ResourceOwnerChecker::getResourceType, c -> c));
    }
    
    public boolean isOwner(String userId, String resourceType, String resourceId) {
        ResourceOwnerChecker checker = checkers.get(resourceType);
        if (checker == null) {
            return false;
        }
        return checker.isOwner(userId, resourceId);
    }
}

public interface ResourceOwnerChecker {
    String getResourceType();
    boolean isOwner(String userId, String resourceId);
}

// Post 资源所有权检查器
@Component
public class PostOwnerChecker implements ResourceOwnerChecker {
    
    private final PostRepository postRepository;
    
    @Override
    public String getResourceType() {
        return "post";
    }
    
    @Override
    public boolean isOwner(String userId, String resourceId) {
        Post post = postRepository.findById(Long.parseLong(resourceId));
        return post != null && post.getOwnerId().equals(userId);
    }
}

// Comment 资源所有权检查器
@Component
public class CommentOwnerChecker implements ResourceOwnerChecker {
    
    private final CommentRepository commentRepository;
    
    @Override
    public String getResourceType() {
        return "comment";
    }
    
    @Override
    public boolean isOwner(String userId, String resourceId) {
        Comment comment = commentRepository.findById(Long.parseLong(resourceId));
        return comment != null && comment.getAuthorId().equals(userId);
    }
}
```

### 5.3 各服务权限应用

```java
// Post Service
@RestController
@RequestMapping("/posts")
public class PostController {
    
    @PostMapping
    @RequireRole("ROLE_AUTHOR")
    public Result<Long> createPost(@RequestBody CreatePostRequest request) { ... }
    
    @PutMapping("/{postId}")
    @RequirePermission("post:update")
    public Result<Void> updatePost(@PathVariable Long postId, 
                                   @RequestBody UpdatePostRequest request) { ... }
    
    @DeleteMapping("/{postId}")
    @RequirePermission({"post:delete", "admin:manage"}, logical = Logical.OR)
    public Result<Void> deletePost(@PathVariable Long postId) { ... }
    
    @PostMapping("/{postId}/publish")
    @RequirePermission("post:publish")
    public Result<Void> publishPost(@PathVariable Long postId) { ... }
}

// Comment Service
@RestController
@RequestMapping("/comments")
public class CommentController {
    
    @PostMapping
    @RequirePermission("comment:create")
    public Result<Long> createComment(@RequestBody CreateCommentRequest request) { ... }
    
    @DeleteMapping("/{commentId}")
    @RequirePermission({"comment:delete", "comment:manage"}, logical = Logical.OR)
    public Result<Void> deleteComment(@PathVariable Long commentId) { ... }
}

// Admin Service
@RestController
@RequestMapping("/admin")
@RequireRole("ROLE_ADMIN")
public class AdminController {
    
    @GetMapping("/dashboard")
    @RequirePermission("admin:dashboard")
    public Result<DashboardVO> getDashboard() { ... }
    
    @PostMapping("/users/{userId}/ban")
    @RequirePermission("user:ban")
    public Result<Void> banUser(@PathVariable String userId) { ... }
}
```


---

## 6. DataLoader 批量查询优化

### 6.1 问题场景

```java
// ❌ N+1 查询问题
List<PostVO> posts = postRepository.findAll();
for (PostVO post : posts) {
    UserDTO author = userServiceClient.getUserById(post.getAuthorId());  // N 次 RPC
    post.setAuthor(author);
}
```

### 6.2 DataLoader 公共模块

> **批量查询大小限制**
> 
> 为防止单次查询数据量过大导致超时或内存问题，所有 DataLoader 实现必须遵循以下限制：
> - 单次批量查询最大 100 条记录
> - 超过限制时自动分批查询
> - 配置化管理，可根据服务能力调整

```java
// common-dataloader 模块
package com.blog.common.dataloader;

public interface DataLoader<K, V> {
    Map<K, V> batchLoad(Set<K> keys);
}

/**
 * DataLoader 配置
 */
@Configuration
@ConfigurationProperties(prefix = "dataloader")
public class DataLoaderConfig {
    /**
     * 单次批量查询最大数量
     */
    private int maxBatchSize = 100;
    
    /**
     * 批量查询超时时间（毫秒）
     */
    private int batchTimeout = 5000;
    
    // getters and setters
}

/**
 * DataLoader 基类，提供分批查询能力
 */
public abstract class AbstractDataLoader<K, V> implements DataLoader<K, V> {
    
    protected final DataLoaderConfig config;
    
    protected AbstractDataLoader(DataLoaderConfig config) {
        this.config = config;
    }
    
    @Override
    public Map<K, V> batchLoad(Set<K> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // 如果超过最大批量大小，分批查询
        if (keys.size() > config.getMaxBatchSize()) {
            return batchLoadInChunks(keys);
        }
        
        return doBatchLoad(keys);
    }
    
    /**
     * 分批查询
     */
    private Map<K, V> batchLoadInChunks(Set<K> keys) {
        Map<K, V> result = new HashMap<>();
        List<K> keyList = new ArrayList<>(keys);
        
        for (int i = 0; i < keyList.size(); i += config.getMaxBatchSize()) {
            int end = Math.min(i + config.getMaxBatchSize(), keyList.size());
            Set<K> chunk = new HashSet<>(keyList.subList(i, end));
            result.putAll(doBatchLoad(chunk));
        }
        
        return result;
    }
    
    /**
     * 子类实现实际的批量查询逻辑
     */
    protected abstract Map<K, V> doBatchLoad(Set<K> keys);
}

// 用户信息加载器
@Component
public class UserDataLoader extends AbstractDataLoader<String, UserBriefDTO> {
    
    private final UserServiceClient userServiceClient;
    
    public UserDataLoader(DataLoaderConfig config, UserServiceClient userServiceClient) {
        super(config);
        this.userServiceClient = userServiceClient;
    }
    
    @Override
    protected Map<String, UserBriefDTO> doBatchLoad(Set<String> userIds) {
        return userServiceClient.batchGetUsers(new ArrayList<>(userIds));
    }
}

// 文章统计加载器
@Component
public class PostStatsDataLoader extends AbstractDataLoader<Long, PostStats> {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public PostStatsDataLoader(DataLoaderConfig config, RedisTemplate<String, Object> redisTemplate) {
        super(config);
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    protected Map<Long, PostStats> doBatchLoad(Set<Long> postIds) {
        List<String> keys = postIds.stream()
            .map(PostRedisKeys::stats)
            .collect(Collectors.toList());
        
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        
        Map<Long, PostStats> result = new HashMap<>();
        int i = 0;
        for (Long postId : postIds) {
            Object value = values.get(i++);
            result.put(postId, value != null ? (PostStats) value : PostStats.empty());
        }
        return result;
    }
}
```

### 6.3 各服务应用

```java
// Post Service - 文章列表查询
@Service
public class PostQueryService {
    
    private final PostRepository postRepository;
    private final UserDataLoader userDataLoader;
    private final PostStatsDataLoader statsDataLoader;
    
    @ReadOnly
    public List<PostVO> getPostList(Long topicId, int page, int size) {
        // 1. 批量查询文章
        List<Post> posts = postRepository.findByTopicId(topicId, page, size);
        
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 2. 收集所有需要的 ID
        Set<String> authorIds = posts.stream()
            .map(Post::getOwnerId)
            .collect(Collectors.toSet());
        Set<Long> postIds = posts.stream()
            .map(Post::getId)
            .collect(Collectors.toSet());
        
        // 3. 批量加载关联数据（2 次调用代替 N 次）
        Map<String, UserBriefDTO> authors = userDataLoader.batchLoad(authorIds);
        Map<Long, PostStats> stats = statsDataLoader.batchLoad(postIds);
        
        // 4. 组装结果
        return posts.stream()
            .map(post -> PostVO.builder()
                .id(post.getId())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .author(authors.get(post.getOwnerId()))
                .stats(stats.getOrDefault(post.getId(), PostStats.empty()))
                .publishedAt(post.getPublishedAt())
                .build())
            .collect(Collectors.toList());
    }
}

// Comment Service - 评论列表查询
@Service
public class CommentQueryService {
    
    private final CommentRepository commentRepository;
    private final UserDataLoader userDataLoader;
    
    @ReadOnly
    public List<CommentVO> getCommentList(Long postId, int page, int size) {
        List<Comment> comments = commentRepository.findByPostId(postId, page, size);
        
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 收集所有用户 ID（作者 + 被回复用户）
        Set<String> userIds = new HashSet<>();
        comments.forEach(c -> {
            userIds.add(c.getAuthorId());
            if (c.getReplyToUserId() != null) {
                userIds.add(c.getReplyToUserId());
            }
        });
        
        // 批量加载用户信息
        Map<String, UserBriefDTO> users = userDataLoader.batchLoad(userIds);
        
        return comments.stream()
            .map(c -> CommentVO.builder()
                .id(c.getId())
                .content(c.getContent())
                .author(users.get(c.getAuthorId()))
                .replyToUser(c.getReplyToUserId() != null ? users.get(c.getReplyToUserId()) : null)
                .createdAt(c.getCreatedAt())
                .build())
            .collect(Collectors.toList());
    }
}

// Notification Service - 通知列表查询
@Service
public class NotificationQueryService {
    
    private final NotificationRepository notificationRepository;
    private final UserDataLoader userDataLoader;
    private final PostDataLoader postDataLoader;
    
    @ReadOnly
    public List<NotificationVO> getNotifications(String userId, int page, int size) {
        List<Notification> notifications = notificationRepository.findByRecipientId(userId, page, size);
        
        // 收集关联 ID
        Set<String> senderIds = notifications.stream()
            .map(Notification::getSenderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> postIds = notifications.stream()
            .map(Notification::getTargetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        // 批量加载
        Map<String, UserBriefDTO> senders = userDataLoader.batchLoad(senderIds);
        Map<Long, PostBriefDTO> posts = postDataLoader.batchLoad(postIds);
        
        return assembleNotificationVOs(notifications, senders, posts);
    }
}
```


---

## 7. 统一分页策略

### 7.1 分页策略规范

> **设计说明：统一分页策略**
> 
> 为降低维护成本，所有服务统一使用以下分页策略：

| 场景 | 分页方式 | 适用端 | 说明 |
|------|---------|-------|------|
| 列表浏览（支持跳页） | Offset 分页 | Web 端 | 用户可能需要跳转到特定页 |
| 无限滚动加载 | Cursor 分页 | 移动端 | 性能更好，适合连续加载 |
| 混合分页 | 前 N 页 Offset，之后 Cursor | 通用 | 兼顾跳页需求和性能 |

### 7.2 分页配置

```java
// common-pagination 模块
@Configuration
@ConfigurationProperties(prefix = "pagination")
public class PaginationConfig {
    
    /**
     * 默认每页大小
     */
    private int defaultPageSize = 20;
    
    /**
     * 最大每页大小
     */
    private int maxPageSize = 100;
    
    /**
     * 混合分页阈值：前 N 页使用 Offset，之后使用 Cursor
     */
    private int hybridThreshold = 5;
    
    /**
     * Offset 分页最大页数（防止深度分页）
     */
    private int maxOffsetPage = 100;
    
    // getters and setters
}
```

### 7.3 分页工具类

```java
/**
 * 统一分页请求
 */
@Data
public class PageRequest {
    private Integer page;           // Offset 分页页码（从 0 开始）
    private String cursor;          // Cursor 分页游标
    private int size = 20;          // 每页大小
    private String sortBy;          // 排序字段
    private String sortOrder;       // 排序方向 (ASC/DESC)
    
    public boolean isOffsetMode() {
        return page != null && cursor == null;
    }
    
    public boolean isCursorMode() {
        return cursor != null;
    }
}

/**
 * 统一分页响应
 */
@Data
@Builder
public class PageResponse<T> {
    private List<T> items;
    private int page;               // 当前页码（Offset 模式）
    private int size;               // 每页大小
    private long total;             // 总数（Offset 模式）
    private String nextCursor;      // 下一页游标（Cursor 模式）
    private boolean hasMore;        // 是否有更多数据
    
    public static <T> PageResponse<T> ofOffset(List<T> items, int page, int size, long total) {
        return PageResponse.<T>builder()
            .items(items)
            .page(page)
            .size(size)
            .total(total)
            .hasMore((long) (page + 1) * size < total)
            .build();
    }
    
    public static <T> PageResponse<T> ofCursor(List<T> items, String nextCursor, boolean hasMore) {
        return PageResponse.<T>builder()
            .items(items)
            .nextCursor(nextCursor)
            .hasMore(hasMore)
            .build();
    }
}

/**
 * 分页服务基类
 */
public abstract class AbstractPaginationService<T, C> {
    
    protected final PaginationConfig config;
    
    /**
     * 混合分页查询
     * 前 N 页使用 Offset，之后自动切换到 Cursor
     */
    public PageResponse<T> query(PageRequest request) {
        // 验证参数
        int size = Math.min(request.getSize(), config.getMaxPageSize());
        
        if (request.isCursorMode()) {
            // Cursor 模式
            return queryCursor(request.getCursor(), size);
        }
        
        int page = request.getPage() != null ? request.getPage() : 0;
        
        // 检查是否超过混合分页阈值
        if (page >= config.getHybridThreshold()) {
            // 超过阈值，建议使用 Cursor 模式
            // 但仍然支持 Offset（有最大页数限制）
            if (page > config.getMaxOffsetPage()) {
                throw new BusinessException("页码超出限制，请使用游标分页");
            }
        }
        
        return queryOffset(page, size);
    }
    
    protected abstract PageResponse<T> queryOffset(int page, int size);
    protected abstract PageResponse<T> queryCursor(String cursor, int size);
}
```

### 7.4 各服务应用示例

```java
// Post Service - 文章列表
@Service
public class PostListService extends AbstractPaginationService<PostVO, TimeCursor> {
    
    @Override
    protected PageResponse<PostVO> queryOffset(int page, int size) {
        Page<Post> postPage = postRepository.findPublished(page, size);
        List<PostVO> voList = assemblePostVOList(postPage.getContent());
        return PageResponse.ofOffset(voList, page, size, postPage.getTotalElements());
    }
    
    @Override
    protected PageResponse<PostVO> queryCursor(String cursor, int size) {
        TimeCursor timeCursor = timeCursorCodec.decode(cursor);
        List<Post> posts = postRepository.findPublishedCursor(timeCursor, size + 1);
        
        boolean hasMore = posts.size() > size;
        String nextCursor = null;
        if (hasMore) {
            posts = posts.subList(0, size);
            Post lastPost = posts.get(posts.size() - 1);
            nextCursor = timeCursorCodec.encode(lastPost.getPublishedAt(), lastPost.getId());
        }
        
        List<PostVO> voList = assemblePostVOList(posts);
        return PageResponse.ofCursor(voList, nextCursor, hasMore);
    }
}

// Comment Service - 评论列表
@Service
public class CommentListService extends AbstractPaginationService<CommentVO, TimeCursor> {
    
    // 类似实现...
}
```

---

## 8. 读写分离设计

### 8.1 数据源配置

```java
// common-datasource 模块
package com.blog.common.datasource;

public enum DataSourceType {
    MASTER, SLAVE
}

public class DataSourceContextHolder {
    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();
    
    public static void setDataSourceType(DataSourceType type) {
        CONTEXT.set(type);
    }
    
    public static DataSourceType getDataSourceType() {
        return CONTEXT.get();
    }
    
    public static void clear() {
        CONTEXT.remove();
    }
}

public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DataSourceContextHolder.getDataSourceType();
        return type != null ? type : DataSourceType.MASTER;
    }
}

@Configuration
@ConditionalOnProperty(name = "datasource.read-write-split.enabled", havingValue = "true")
public class DataSourceConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.slave")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
    
    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource master,
            @Qualifier("slaveDataSource") DataSource slave) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.MASTER, master);
        targetDataSources.put(DataSourceType.SLAVE, slave);
        
        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(master);
        return routingDataSource;
    }
}
```

### 8.2 读写分离注解

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadOnly {
}

@Aspect
@Component
@Order(0)  // 在事务之前执行
public class DataSourceAspect {
    
    @Around("@annotation(readOnly)")
    public Object switchToSlave(ProceedingJoinPoint joinPoint, ReadOnly readOnly) throws Throwable {
        try {
            DataSourceContextHolder.setDataSourceType(DataSourceType.SLAVE);
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
    
    // 也支持 @Transactional(readOnly = true) 自动切换
    @Around("@annotation(transactional) && @annotation(transactional).readOnly()")
    public Object switchToSlaveForReadOnlyTransaction(ProceedingJoinPoint joinPoint, 
                                                       Transactional transactional) throws Throwable {
        if (transactional.readOnly()) {
            try {
                DataSourceContextHolder.setDataSourceType(DataSourceType.SLAVE);
                return joinPoint.proceed();
            } finally {
                DataSourceContextHolder.clear();
            }
        }
        return joinPoint.proceed();
    }
}
```

### 8.3 各服务应用

```yaml
# post-service application.yml
datasource:
  read-write-split:
    enabled: true

spring:
  datasource:
    master:
      jdbc-url: jdbc:postgresql://master-db:5432/post_db
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
    slave:
      jdbc-url: jdbc:postgresql://slave-db:5432/post_db
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 30
        minimum-idle: 10
```

```java
// Post Service 应用
@Service
public class PostApplicationService {
    
    // 写操作 - 使用主库
    @Transactional
    public Long createPost(String userId, CreatePostRequest request) {
        // 自动使用 MASTER
        Post post = Post.createDraft(...);
        postRepository.save(post);
        return post.getId();
    }
    
    // 读操作 - 使用从库
    @ReadOnly
    public PostVO getPostById(Long postId) {
        // 自动使用 SLAVE
        Post post = postRepository.findById(postId);
        return assemblePostVO(post);
    }
    
    // 或者使用 @Transactional(readOnly = true)
    @Transactional(readOnly = true)
    public List<PostVO> getPostList(int page, int size) {
        // 自动使用 SLAVE
        return postRepository.findPublished(page, size);
    }
}

// Comment Service 应用
@Service
public class CommentApplicationService {
    
    @Transactional
    public Long createComment(String userId, CreateCommentRequest request) {
        // MASTER
    }
    
    @ReadOnly
    public Page<CommentVO> getCommentsByPage(Long postId, int page, int size) {
        // SLAVE
    }
    
    @ReadOnly
    public CursorPage<CommentVO> getCommentsByCursor(Long postId, String cursor, int size) {
        // SLAVE
    }
}
```

---

## 9. 公共模块依赖关系

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              公共模块依赖图                                       │
│                                                                                  │
│   blog-common-core          ← 基础工具类、异常定义、Result 封装                    │
│        ↑                                                                         │
│   blog-common-web           ← API 版本控制、全局异常处理、限流响应                  │
│        ↑                                                                         │
│   blog-common-security      ← RBAC 权限注解、SecurityContext                      │
│        ↑                                                                         │
│   blog-common-datasource    ← 读写分离、数据源路由                                 │
│        ↑                                                                         │
│   blog-common-dataloader    ← DataLoader 接口、批量查询优化                        │
│        ↑                                                                         │
│   blog-common-saga          ← Saga 编排器、补偿机制                                │
│        ↑                                                                         │
│   blog-common-metrics       ← Prometheus 指标、业务指标基类                        │
│        ↑                                                                         │
│   blog-common-sentinel      ← Sentinel 限流配置、熔断降级                          │
│                                                                                  │
│   各业务服务按需引入：                                                             │
│   - user-service:    core, web, security, datasource, dataloader, metrics        │
│   - post-service:    core, web, security, datasource, dataloader, metrics, saga  │
│   - comment-service: core, web, security, datasource, dataloader, metrics        │
│   - message-service: core, web, security, datasource, dataloader, metrics        │
│   - notification-service: core, web, datasource, dataloader, metrics             │
│   - search-service:  core, web, metrics                                          │
│   - ranking-service: core, web, metrics                                          │
│   - upload-service:  core, web, security, metrics                                │
│   - admin-service:   core, web, security, datasource, dataloader, metrics        │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Maven 依赖配置

```xml
<!-- 父 POM -->
<modules>
    <module>blog-common-core</module>
    <module>blog-common-web</module>
    <module>blog-common-security</module>
    <module>blog-common-datasource</module>
    <module>blog-common-dataloader</module>
    <module>blog-common-saga</module>
    <module>blog-common-metrics</module>
    <module>blog-common-sentinel</module>
</modules>

<!-- post-service pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.blog</groupId>
        <artifactId>blog-common-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.blog</groupId>
        <artifactId>blog-common-security</artifactId>
    </dependency>
    <dependency>
        <groupId>com.blog</groupId>
        <artifactId>blog-common-datasource</artifactId>
    </dependency>
    <dependency>
        <groupId>com.blog</groupId>
        <artifactId>blog-common-dataloader</artifactId>
    </dependency>
    <dependency>
        <groupId>com.blog</groupId>
        <artifactId>blog-common-metrics</artifactId>
    </dependency>
    <dependency>
        <groupId>com.blog</groupId>
        <artifactId>blog-common-saga</artifactId>
    </dependency>
</dependencies>
```
