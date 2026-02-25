# API Gateway 设计

## 职责

- 统一入口，路由转发
- JWT Token 验证
- 请求限流（Sentinel）
- 跨域处理
- 请求日志记录

## 路由配置

> **设计说明：统一使用版本化 API 路径**
> 
> 所有 API 路径统一使用 `/api/v1/*` 格式，Gateway 使用 `StripPrefix=2` 去掉 `/api/v1` 前缀，
> 下游服务接收到的路径为 `/users/**`、`/posts/**` 等。这样设计的好处：
> 1. 统一的版本控制策略
> 2. 便于后续 API 版本升级（v2、v3）
> 3. 与 `15-enhancements.md` 中的 API 版本控制策略保持一致

```yaml
spring:
  cloud:
    gateway:
      routes:
        # User Service - V1
        - id: user-service-v1
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**, /api/v1/auth/**
          filters:
            - StripPrefix=2  # 去掉 /api/v1，保留 /users/** 或 /auth/**
            
        # Post Service - V1
        - id: post-service-v1
          uri: lb://post-service
          predicates:
            - Path=/api/v1/posts/**, /api/v1/categories/**
          filters:
            - StripPrefix=2
            
        # Comment Service - V1
        - id: comment-service-v1
          uri: lb://comment-service
          predicates:
            - Path=/api/v1/comments/**
          filters:
            - StripPrefix=2
            
        # Message Service - V1
        - id: message-service-v1
          uri: lb://message-service
          predicates:
            - Path=/api/v1/messages/**, /api/v1/conversations/**
          filters:
            - StripPrefix=2
            
        # Notification Service - V1
        - id: notification-service-v1
          uri: lb://notification-service
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - StripPrefix=2
            
        # Search Service - V1
        - id: search-service-v1
          uri: lb://search-service
          predicates:
            - Path=/api/v1/search/**
          filters:
            - StripPrefix=2
            
        # Ranking Service - V1
        - id: ranking-service-v1
          uri: lb://ranking-service
          predicates:
            - Path=/api/v1/rankings/**
          filters:
            - StripPrefix=2
            
        # Upload Service - V1
        - id: upload-service-v1
          uri: lb://upload-service
          predicates:
            - Path=/api/v1/uploads/**
          filters:
            - StripPrefix=2
            
        # Admin Service - V1
        - id: admin-service-v1
          uri: lb://admin-service
          predicates:
            - Path=/api/v1/admin/**
          filters:
            - StripPrefix=2
```

## JWT 认证过滤器

```java
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final List<String> whiteList = Arrays.asList(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/posts/public/**",
        "/api/v1/search/**"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 白名单放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }
        
        // 验证 Token
        String token = extractToken(exchange.getRequest());
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        // 检查 Token 黑名单（用户登出、被禁用时 Token 会加入黑名单）
        if (tokenBlacklistService.isBlacklisted(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        // 将用户信息传递给下游服务
        String userId = jwtTokenProvider.getUserIdFromToken(token);
        ServerHttpRequest request = exchange.getRequest().mutate()
            .header("X-User-Id", userId)
            .build();
        
        return chain.filter(exchange.mutate().request(request).build());
    }
    
    @Override
    public int getOrder() {
        return -100;
    }
}
```

## Token 黑名单服务

> **设计说明：Token 黑名单机制**
> 
> 用于处理以下场景：
> 1. 用户主动登出
> 2. 用户被管理员禁用
> 3. 用户修改密码后使旧 Token 失效
> 4. 检测到异常登录时强制下线

```java
@Service
public class TokenBlacklistService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    
    /**
     * 将 Token 加入黑名单
     * 
     * @param token JWT Token
     * @param expireAt Token 原本的过期时间（黑名单记录只需保留到 Token 过期）
     */
    public void addToBlacklist(String token, LocalDateTime expireAt) {
        String key = BLACKLIST_PREFIX + hashToken(token);
        Duration ttl = Duration.between(LocalDateTime.now(), expireAt);
        
        if (ttl.isPositive()) {
            redisTemplate.opsForValue().set(key, "1", ttl);
        }
    }
    
    /**
     * 将用户的所有 Token 加入黑名单（用于禁用用户、修改密码等场景）
     */
    public void blacklistAllUserTokens(String userId) {
        String key = BLACKLIST_PREFIX + "user:" + userId;
        // 记录用户被禁用的时间，所有在此时间之前签发的 Token 都无效
        redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), Duration.ofDays(7));
    }
    
    /**
     * 检查 Token 是否在黑名单中
     */
    public boolean isBlacklisted(String token) {
        // 检查单个 Token 黑名单
        String tokenKey = BLACKLIST_PREFIX + hashToken(token);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey))) {
            return true;
        }
        
        // 检查用户级别黑名单
        String userId = jwtTokenProvider.getUserIdFromToken(token);
        LocalDateTime tokenIssuedAt = jwtTokenProvider.getIssuedAtFromToken(token);
        String userKey = BLACKLIST_PREFIX + "user:" + userId;
        
        String blacklistTime = (String) redisTemplate.opsForValue().get(userKey);
        if (blacklistTime != null) {
            LocalDateTime blacklistedAt = LocalDateTime.parse(blacklistTime);
            // 如果 Token 签发时间早于黑名单时间，则 Token 无效
            return tokenIssuedAt.isBefore(blacklistedAt);
        }
        
        return false;
    }
    
    /**
     * 对 Token 进行哈希，避免存储完整 Token
     */
    private String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}
```
