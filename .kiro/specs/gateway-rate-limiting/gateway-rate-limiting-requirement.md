# 网关用户级别限流需求

## 背景

当前网关测试中发现GW-015（用户级别限流）测试被跳过，因为该功能尚未实现。为了防止恶意用户刷评论、点赞、关注等操作，需要实现基于用户的限流功能。

## 需求描述

### 功能需求

实现基于用户ID的限流功能，对不同类型的操作设置不同的限流规则：

| 操作类型 | 限流规则 | 说明 |
|---------|---------|------|
| 评论 | 10次/分钟 | 防止刷评论 |
| 点赞 | 30次/分钟 | 防止刷点赞 |
| 关注/取消关注 | 20次/分钟 | 防止刷关注 |
| 发布文章 | 5次/分钟 | 防止刷文章 |
| 发送私信 | 10次/分钟 | 防止骚扰 |
| 搜索 | 60次/分钟 | 防止爬虫 |
| 上传文件 | 10次/分钟 | 防止滥用存储 |

### 技术要求

1. **使用Sentinel实现限流**
   - 网关已有Sentinel依赖
   - 使用Sentinel Gateway适配器
   - 支持动态配置（通过Nacos）

2. **限流维度**
   - 基于用户ID（从JWT token中提取）
   - 基于API路径
   - 组合维度：用户ID + API路径

3. **限流策略**
   - 使用滑动窗口算法
   - 支持QPS（每秒请求数）和线程数限流
   - 支持预热（Warm Up）模式

4. **限流响应**
   - HTTP状态码：429 Too Many Requests
   - 响应体：
     ```json
     {
       "code": 429,
       "message": "请求过于频繁，请稍后再试",
       "data": {
         "retryAfter": 60  // 秒
       }
     }
     ```

5. **白名单机制**
   - 管理员用户不受限流限制
   - VIP用户有更高的限流阈值
   - 可通过配置动态调整

## 实现方案

### 1. 创建限流配置类

```java
// ZhiCore-gateway/src/main/java/com/ZhiCore/gateway/config/RateLimitConfig.java
@Configuration
public class RateLimitConfig {
    
    @Bean
    public GatewayRateLimiter gatewayRateLimiter(RedisTemplate<String, String> redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }
    
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // 从请求头中获取用户ID（由JwtAuthenticationFilter设置）
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(userId != null ? userId : "anonymous");
        };
    }
    
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getPath().value());
    }
    
    @Bean
    public KeyResolver userApiKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            String path = exchange.getRequest().getPath().value();
            return Mono.just((userId != null ? userId : "anonymous") + ":" + path);
        };
    }
}
```

### 2. 创建限流规则管理器

```java
// ZhiCore-gateway/src/main/java/com/ZhiCore/gateway/ratelimit/RateLimitRuleManager.java
@Component
public class RateLimitRuleManager {
    
    private final Map<String, RateLimitRule> rules = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 评论限流：10次/分钟
        rules.put("/api/v1/comments", new RateLimitRule(10, 60));
        
        // 点赞限流：30次/分钟
        rules.put("/api/v1/posts/*/like", new RateLimitRule(30, 60));
        rules.put("/api/v1/comments/*/like", new RateLimitRule(30, 60));
        
        // 关注限流：20次/分钟
        rules.put("/api/v1/users/*/following/*", new RateLimitRule(20, 60));
        
        // 发布文章限流：5次/分钟
        rules.put("/api/v1/posts", new RateLimitRule(5, 60));
        
        // 发送私信限流：10次/分钟
        rules.put("/api/v1/messages", new RateLimitRule(10, 60));
        
        // 搜索限流：60次/分钟
        rules.put("/api/v1/search/**", new RateLimitRule(60, 60));
        
        // 上传限流：10次/分钟
        rules.put("/api/v1/upload/**", new RateLimitRule(10, 60));
    }
    
    public RateLimitRule getRule(String path) {
        return rules.entrySet().stream()
            .filter(entry -> pathMatcher.match(entry.getKey(), path))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
}
```

### 3. 创建限流过滤器

```java
// ZhiCore-gateway/src/main/java/com/ZhiCore/gateway/filter/RateLimitFilter.java
@Component
@Order(-50)  // 在JWT过滤器之后执行
public class RateLimitFilter implements GlobalFilter {
    
    private final RateLimitRuleManager ruleManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String userId = request.getHeaders().getFirst("X-User-Id");
        
        // 检查是否需要限流
        RateLimitRule rule = ruleManager.getRule(path);
        if (rule == null || userId == null) {
            return chain.filter(exchange);
        }
        
        // 检查用户是否在白名单中
        if (isWhitelisted(userId)) {
            return chain.filter(exchange);
        }
        
        // 执行限流检查
        String key = "rate_limit:" + userId + ":" + path;
        return checkRateLimit(key, rule)
            .flatMap(allowed -> {
                if (allowed) {
                    return chain.filter(exchange);
                } else {
                    return rateLimitExceeded(exchange, rule);
                }
            });
    }
    
    private Mono<Boolean> checkRateLimit(String key, RateLimitRule rule) {
        // 使用Redis实现滑动窗口限流
        return Mono.fromCallable(() -> {
            long now = System.currentTimeMillis();
            long windowStart = now - rule.getWindowSeconds() * 1000;
            
            // 移除过期的记录
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            
            // 获取当前窗口内的请求数
            Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
            
            if (count < rule.getLimit()) {
                // 添加当前请求
                redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
                redisTemplate.expire(key, rule.getWindowSeconds(), TimeUnit.SECONDS);
                return true;
            }
            
            return false;
        });
    }
    
    private Mono<Void> rateLimitExceeded(ServerWebExchange exchange, RateLimitRule rule) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(rule.getLimit()));
        response.getHeaders().add("X-RateLimit-Remaining", "0");
        response.getHeaders().add("Retry-After", String.valueOf(rule.getWindowSeconds()));
        
        String body = String.format(
            "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":{\"retryAfter\":%d}}",
            rule.getWindowSeconds()
        );
        
        return response.writeWith(Mono.just(
            response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
        ));
    }
    
    private boolean isWhitelisted(String userId) {
        // TODO: 从配置或数据库中检查用户是否在白名单中
        return false;
    }
}
```

### 4. 配置Sentinel（可选，用于更高级的限流）

```yaml
# application.yml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080
      datasource:
        ds1:
          nacos:
            server-addr: localhost:8848
            dataId: gateway-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: flow
```

### 5. Nacos动态配置

```json
// Nacos配置：gateway-flow-rules
[
  {
    "resource": "comment_api",
    "grade": 1,
    "count": 10,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  },
  {
    "resource": "like_api",
    "grade": 1,
    "count": 30,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

## 测试方案

### 单元测试

```java
@Test
public void testUserRateLimitExceeded() {
    // 模拟用户在1分钟内发送11次评论请求
    String userId = "test-user-123";
    String path = "/api/v1/comments";
    
    for (int i = 0; i < 10; i++) {
        // 前10次应该成功
        boolean allowed = rateLimitFilter.checkRateLimit(userId, path);
        assertTrue(allowed);
    }
    
    // 第11次应该被限流
    boolean allowed = rateLimitFilter.checkRateLimit(userId, path);
    assertFalse(allowed);
}
```

### 集成测试

更新 `tests/api/gateway/test-gateway-api-full.ps1` 中的 GW-015 测试：

```powershell
# GW-015: User-based Rate Limiting
Write-Host "[GW-015] Testing user-based rate limiting..." -ForegroundColor Yellow
if ([string]::IsNullOrEmpty($Global:AccessToken)) {
    Add-TestResult -TestId "GW-015" -TestName "User Rate Limiting" -Status "SKIP" -ResponseTime "-" -Note "No token available"
    Write-Host "  SKIP - No token available" -ForegroundColor Gray
} else {
    # 快速发送多个评论请求
    $CommentBody = @{ postId = $Global:TestPostId; content = "Rate limit test" }
    $SuccessCount = 0
    $RateLimitedCount = 0
    
    for ($i = 1; $i -le 15; $i++) {
        $Result = Invoke-ApiRequest -Method "POST" -Url "$GatewayUrl/api/v1/comments" -Body $CommentBody -Headers (Get-AuthHeaders)
        
        if ($Result.Success) {
            $SuccessCount++
        } elseif ($Result.StatusCode -eq 429) {
            $RateLimitedCount++
        }
        
        Start-Sleep -Milliseconds 100
    }
    
    if ($RateLimitedCount -gt 0) {
        Add-TestResult -TestId "GW-015" -TestName "User Rate Limiting" -Status "PASS" -ResponseTime "-" -Note "Rate limit triggered ($SuccessCount allowed, $RateLimitedCount blocked)"
        Write-Host "  PASS - User rate limiting working ($SuccessCount allowed, $RateLimitedCount blocked)" -ForegroundColor Green
    } else {
        Add-TestResult -TestId "GW-015" -TestName "User Rate Limiting" -Status "FAIL" -ResponseTime "-" -Note "Rate limit not triggered"
        Write-Host "  FAIL - Rate limit not triggered (all $SuccessCount requests succeeded)" -ForegroundColor Red
    }
}
```

## 实施步骤

1. **Phase 1: 基础实现**（1-2天）
   - [ ] 创建RateLimitConfig配置类
   - [ ] 创建RateLimitRuleManager规则管理器
   - [ ] 创建RateLimitFilter过滤器
   - [ ] 实现基于Redis的滑动窗口限流

2. **Phase 2: 规则配置**（1天）
   - [ ] 定义各API的限流规则
   - [ ] 实现白名单机制
   - [ ] 支持VIP用户更高阈值

3. **Phase 3: 动态配置**（1-2天）
   - [ ] 集成Sentinel
   - [ ] 配置Nacos数据源
   - [ ] 实现规则动态更新

4. **Phase 4: 测试**（1天）
   - [ ] 编写单元测试
   - [ ] 更新集成测试
   - [ ] 压力测试验证

5. **Phase 5: 监控告警**（1天）
   - [ ] 添加限流指标监控
   - [ ] 配置Grafana仪表板
   - [ ] 设置告警规则

## 预期效果

实施后的效果：

1. **安全性提升**
   - 防止恶意用户刷评论、点赞
   - 防止爬虫过度抓取
   - 防止DDoS攻击

2. **系统稳定性**
   - 保护后端服务不被过载
   - 确保正常用户的服务质量
   - 降低系统资源消耗

3. **可观测性**
   - 实时监控限流情况
   - 识别异常用户行为
   - 数据驱动的规则优化

## 相关文档

- [Sentinel官方文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [Spring Cloud Gateway限流](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#the-requestratelimiter-gatewayfilter-factory)
- [Redis滑动窗口限流算法](https://redis.io/commands/zadd)

## 优先级

**优先级**: 高  
**原因**: 防止恶意刷操作是系统安全的重要组成部分

## 估算工作量

- **开发**: 3-4天
- **测试**: 1-2天
- **总计**: 4-6天

---

**创建日期**: 2026-01-21  
**创建人**: API Testing Team  
**状态**: 待实施
