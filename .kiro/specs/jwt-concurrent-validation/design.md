# Design Document

## Overview

本设计文档针对 ZhiCore-gateway 在高并发场景下出现的 JWT 签名验证失败问题提供解决方案。问题的核心在于 `JwtAuthenticationFilter` 在处理并发请求时存在性能问题，每次请求都重复创建 `SecretKey` 和 `JwtParser` 实例。

### 问题分析

**症状**:
```
2026-01-21 19:07:33.878 [lettuce-nioEventLoop-5-1] [] ERROR c.b.g.filter.JwtAuthenticationFilter 
- Token validation failed: JWT signature does not match locally computed signature. 
JWT validity cannot be asserted and should not be trusted.
```

**根本原因**:

经过代码分析，发现项目中有 **三处** JWT 解析实现存在相同的问题：

1. **重复创建 SecretKey**: `Keys.hmacShaKeyFor(secret.getBytes())` 在每次验证时都创建新的 `SecretKey` 对象
2. **重复创建 JwtParser**: `Jwts.parser().verifyWith(key).build()` 在每次验证时都创建新的 `JwtParser` 实例
3. **资源浪费**: 这种模式在高并发下产生大量临时对象，增加 GC 压力
4. **缺少复用**: 未利用 JJWT 库的线程安全特性

### 问题代码位置

| 文件 | 位置 | 问题 |
|------|------|------|
| `JwtAuthenticationFilter.java` | 第 75-80 行 | 每次请求创建 SecretKey 和 JwtParser |
| `JwtTokenProvider.java` | 第 129-145 行 | 每次解析创建 SecretKey 和 JwtParser |
| `UserContextFilter.java` | 第 81-88 行 | 每次过滤创建 SecretKey 和 JwtParser |

### JJWT 官方最佳实践

根据 JJWT 官方文档和 StackOverflow 的官方回答：

1. **`JwtParser` 通过 `Jwts.parser().build()` 创建后是不可变的 (immutable)**
2. **不可变的 `JwtParser` 是线程安全的 (thread-safe)**
3. **应该预先创建并复用同一个 `JwtParser` 实例**，避免重复创建开销
4. **`SecretKey` 也是不可变的**，同样应该预先创建并复用

## Architecture

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      API Gateway                             │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         JwtAuthenticationFilter (Order: -100)          │ │
│  │                                                         │
│  │  1. Extract Token from Authorization Header            │ │
│  │  2. Check Whitelist Path                               │ │
│  │  3. Check Token Blacklist (Redis)                      │ │
│  │  4. Validate Token (with Cache)                        │ │
│  │  5. Add User Info to Request Headers                   │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              JwtTokenValidator                          │ │
│  │                                                         │
│  │  - Pre-computed SecretKey (immutable, singleton)       │ │
│  │  - Pre-built JwtParser (immutable, thread-safe)        │ │
│  │  - Token Validation Cache (Caffeine)                   │ │
│  │  - Metrics Collection (Micrometer)                     │ │
│  └────────────────────────────────────────────────────────┘ │
│                          │                                   │
│                          ▼                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              TokenValidationCache                       │ │
│  │                                                         │
│  │  - Cache Size: 10,000 tokens                           │ │
│  │  - TTL: 5 minutes                                      │ │
│  │  - Eviction: LRU                                       │ │
│  │  - Key: Token Hash (SHA-256)                           │ │
│  │  - Value: ValidationResult (userId, userName, roles)   │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 验证流程

```
Request with JWT Token
        │
        ▼
┌───────────────────┐
│ Is Whitelist Path?│──Yes──> Forward to Service
└───────────────────┘
        │ No
        ▼
┌───────────────────┐
│ Extract Token     │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ Token Exists?     │──No──> Return 401
└───────────────────┘
        │ Yes
        ▼
┌───────────────────┐
│ Check Blacklist   │──Yes──> Return 401 (Revoked)
└───────────────────┘
        │ No
        ▼
┌───────────────────┐
│ Check Cache       │──Hit──> Extract User Info
└───────────────────┘         │
        │ Miss                │
        ▼                     │
┌───────────────────┐         │
│ Validate Signature│         │
│ (Thread-safe)     │         │
└───────────────────┘         │
        │                     │
        ▼                     │
┌───────────────────┐         │
│ Parse Claims      │         │
└───────────────────┘         │
        │                     │
        ▼                     │
┌───────────────────┐         │
│ Cache Result      │         │
└───────────────────┘         │
        │                     │
        └─────────┬───────────┘
                  ▼
        ┌───────────────────┐
        │ Add User Headers  │
        └───────────────────┘
                  │
                  ▼
        Forward to Service
```

## Components and Interfaces

### 1. JwtTokenValidator

**职责**: 线程安全的 JWT Token 验证器，核心组件

**最佳实践实现**:

```java
package com.ZhiCore.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenValidator {
    
    // Pre-computed immutable SecretKey (singleton)
    private final SecretKey secretKey;
    
    // Pre-built immutable JwtParser (thread-safe, singleton)
    private final JwtParser jwtParser;
    
    private final TokenValidationCache validationCache;
    private final JwtMetricsCollector metricsCollector;
    
    public JwtTokenValidator(JwtProperties jwtProperties,
                            TokenValidationCache validationCache,
                            JwtMetricsCollector metricsCollector) {
        // [CRITICAL] Pre-compute secret key ONCE at startup (immutable)
        this.secretKey = Keys.hmacShaKeyFor(
            jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
        
        // [CRITICAL] Pre-build JwtParser ONCE at startup (immutable, thread-safe)
        // According to JJWT documentation, JwtParser created via builder is:
        // 1. Immutable - cannot be modified after build()
        // 2. Thread-safe - safe to share across multiple threads
        // 3. Reusable - should be reused to avoid object creation overhead
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
        
        this.validationCache = validationCache;
        this.metricsCollector = metricsCollector;
        
        log.info("JwtTokenValidator initialized with pre-built JwtParser (thread-safe singleton)");
    }
    
    /**
     * Validate JWT token with caching
     * Thread-safe method - can be called concurrently from multiple threads
     */
    public Optional<ValidationResult> validate(String token) {
        long startTime = System.nanoTime();
        
        try {
            // Check cache first
            Optional<ValidationResult> cached = validationCache.get(token);
            if (cached.isPresent()) {
                metricsCollector.recordCacheHit();
                metricsCollector.recordValidationTime(System.nanoTime() - startTime);
                return cached;
            }
            
            // Cache miss - perform validation
            metricsCollector.recordCacheMiss();
            
            // [THREAD-SAFE] Parse using pre-built immutable JwtParser
            // No object creation per request - reusing singleton instance
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            
            // Extract user info
            ValidationResult result = ValidationResult.builder()
                    .userId(claims.getSubject())
                    .userName(claims.get("userName", String.class))
                    .roles(claims.get("roles", String.class))
                    .build();
            
            // Cache the result
            validationCache.put(token, result);
            
            metricsCollector.recordSuccess();
            metricsCollector.recordValidationTime(System.nanoTime() - startTime);
            
            return Optional.of(result);
            
        } catch (ExpiredJwtException e) {
            log.warn("Token expired - userId: {}, expiration: {}", 
                    e.getClaims().getSubject(), 
                    e.getClaims().getExpiration());
            metricsCollector.recordExpired();
            metricsCollector.recordValidationTime(System.nanoTime() - startTime);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Token validation failed - token: {}, error: {}", 
                    maskToken(token), 
                    e.getMessage(), 
                    e);
            metricsCollector.recordFailure(e.getClass().getSimpleName());
            metricsCollector.recordValidationTime(System.nanoTime() - startTime);
            return Optional.empty();
        }
    }
    
    /**
     * Mask token for logging (show first 10 and last 10 chars)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 20) {
            return "***";
        }
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }
}
```

### 2. TokenValidationCache

**职责**: 缓存已验证的 Token，提高性能

**优化点**: 使用 `ThreadLocal<MessageDigest>` 避免 synchronized 瓶颈

```java
package com.ZhiCore.gateway.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
public class TokenValidationCache {
    
    private final Cache<String, ValidationResult> cache;
    
    // Use ThreadLocal to avoid synchronized bottleneck
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    });
    
    private final boolean cacheEnabled;
    
    public TokenValidationCache(JwtProperties jwtProperties) {
        // Check if cache is enabled
        this.cacheEnabled = jwtProperties.getCache() != null && 
                           jwtProperties.getCache().isEnabled();
        
        if (!cacheEnabled) {
            this.cache = null;
            log.warn("TokenValidationCache is DISABLED - all tokens will be validated without caching");
            return;
        }
        
        // Initialize cache with Caffeine
        int maxSize = jwtProperties.getCache().getMaxSize();
        int ttlMinutes = jwtProperties.getCache().getTtlMinutes();
        
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();
        
        log.info("TokenValidationCache initialized - maxSize: {}, ttl: {} minutes", 
                 maxSize, ttlMinutes);
    }
    
    /**
     * Get cached validation result
     */
    public Optional<ValidationResult> get(String token) {
        if (!cacheEnabled || cache == null) {
            return Optional.empty();
        }
        String key = hashToken(token);
        ValidationResult result = cache.getIfPresent(key);
        return Optional.ofNullable(result);
    }
    
    /**
     * Put validation result into cache
     */
    public void put(String token, ValidationResult result) {
        if (!cacheEnabled || cache == null) {
            return;
        }
        String key = hashToken(token);
        cache.put(key, result);
    }
    
    /**
     * Invalidate cached token
     */
    public void invalidate(String token) {
        if (!cacheEnabled || cache == null) {
            return;
        }
        String key = hashToken(token);
        cache.invalidate(key);
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        if (!cacheEnabled || cache == null) {
            return CacheStats.builder()
                    .hitCount(0)
                    .missCount(0)
                    .hitRate(0.0)
                    .evictionCount(0)
                    .size(0)
                    .build();
        }
        var stats = cache.stats();
        return CacheStats.builder()
                .hitCount(stats.hitCount())
                .missCount(stats.missCount())
                .hitRate(stats.hitRate())
                .evictionCount(stats.evictionCount())
                .size(cache.estimatedSize())
                .build();
    }
    
    /**
     * Hash token using SHA-256 for cache key
     * Uses ThreadLocal to avoid synchronized bottleneck
     * This prevents storing full tokens in memory
     */
    private String hashToken(String token) {
        MessageDigest digest = SHA256_DIGEST.get();
        digest.reset();  // Important: reset before use
        byte[] hash = digest.digest(token.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }
}
```

### 3. ValidationResult

**职责**: 封装验证结果

```java
package com.ZhiCore.gateway.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String userName;
    private String roles;
}
```

### 4. JwtMetricsCollector

**职责**: 收集 JWT 验证的指标

```java
package com.ZhiCore.gateway.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtMetricsCollector {
    
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter expiredCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer validationTimer;
    
    public JwtMetricsCollector(MeterRegistry meterRegistry) {
        this.successCounter = Counter.builder("jwt.validation.success")
                .description("Number of successful JWT validations")
                .register(meterRegistry);
        
        this.failureCounter = Counter.builder("jwt.validation.failure")
                .description("Number of failed JWT validations")
                .tag("reason", "signature_mismatch")
                .register(meterRegistry);
        
        this.expiredCounter = Counter.builder("jwt.validation.expired")
                .description("Number of expired JWT tokens")
                .register(meterRegistry);
        
        this.cacheHitCounter = Counter.builder("jwt.cache.hit")
                .description("Number of JWT cache hits")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("jwt.cache.miss")
                .description("Number of JWT cache misses")
                .register(meterRegistry);
        
        this.validationTimer = Timer.builder("jwt.validation.time")
                .description("JWT validation time")
                .register(meterRegistry);
    }
    
    public void recordSuccess() {
        successCounter.increment();
    }
    
    public void recordFailure(String reason) {
        failureCounter.increment();
        log.warn("JWT validation failure recorded - reason: {}", reason);
    }
    
    public void recordExpired() {
        expiredCounter.increment();
    }
    
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }
    
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }
    
    public void recordValidationTime(long nanos) {
        validationTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
}
```

### 5. Updated JwtAuthenticationFilter

**职责**: 使用新的验证器重构过滤器

```java
package com.ZhiCore.gateway.filter;

import com.ZhiCore.gateway.config.JwtProperties;
import com.ZhiCore.gateway.security.JwtTokenValidator;
import com.ZhiCore.gateway.security.ValidationResult;
import com.ZhiCore.gateway.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_NAME_HEADER = "X-User-Name";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenValidator tokenValidator;  // Inject thread-safe validator
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Check whitelist
        if (isWhitelistPath(path)) {
            return chain.filter(exchange);
        }

        // Extract token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "Missing authentication token");
        }

        // Check blacklist
        return tokenBlacklistService.isBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        return unauthorized(exchange, "Token has been revoked");
                    }
                    return validateAndForward(exchange, chain, token);
                });
    }

    private Mono<Void> validateAndForward(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        // Use thread-safe validator with caching
        return Mono.fromCallable(() -> tokenValidator.validate(token))
                .flatMap(resultOpt -> {
                    if (resultOpt.isEmpty()) {
                        return unauthorized(exchange, "Invalid or expired token");
                    }
                    
                    ValidationResult result = resultOpt.get();
                    
                    // Add user info to request headers
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(USER_ID_HEADER, result.getUserId())
                            .header(USER_NAME_HEADER, result.getUserName() != null ? result.getUserName() : "")
                            .header(USER_ROLES_HEADER, result.getRoles() != null ? result.getRoles() : "")
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(e -> {
                    log.error("Unexpected error during token validation", e);
                    return unauthorized(exchange, "Authentication failed");
                });
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean isWhitelistPath(String path) {
        return jwtProperties.getWhitelist().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");

        String body = String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
```

## Data Models

### CacheStats

```java
package com.ZhiCore.gateway.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CacheStats {
    private long hitCount;
    private long missCount;
    private double hitRate;
    private long evictionCount;
    private long size;
}
```

### JwtProperties.CacheConfig

```java
package com.ZhiCore.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;
    private List<String> whitelist;
    private CacheConfig cache;
    
    @Data
    public static class CacheConfig {
        private boolean enabled = true;  // Default: enabled
        private int maxSize = 10000;     // Default: 10,000 tokens
        private int ttlMinutes = 5;      // Default: 5 minutes
    }
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Thread Safety of JWT Validation
*For any* set of concurrent requests with valid JWT tokens, all tokens should be validated correctly without signature mismatch errors.
**Validates: Requirements 2.1, 2.5**

### Property 2: JwtParser Singleton Reuse
*For any* number of concurrent validations, the same pre-built `JwtParser` instance should be used without creating new instances.
**Validates: Performance Optimization**

### Property 3: Cache Consistency
*For any* valid JWT token, if it is validated successfully once, subsequent validations within the cache TTL should return the same result without re-parsing.
**Validates: Requirements 3.1, 3.2, 3.3**

### Property 4: Cache Expiration
*For any* cached validation result, after the TTL expires, the next validation should re-parse the token and update the cache.
**Validates: Requirements 3.4**

### Property 5: Validation Result Correctness
*For any* valid JWT token, the validation result should contain the correct userId, userName, and roles extracted from the token claims.
**Validates: Requirements 2.1**

### Property 6: Error Handling Completeness
*For any* invalid JWT token (expired, malformed, wrong signature), the system should return an appropriate error without throwing unhandled exceptions.
**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**

### Property 7: Metrics Accuracy
*For any* JWT validation attempt, the system should record exactly one metric event (success, failure, expired, cache hit, or cache miss).
**Validates: Requirements 4.1, 4.2**

### Property 8: Blacklist Check Before Validation
*For any* JWT token in the blacklist, the system should reject it before performing signature validation.
**Validates: Requirements 8.3**

### Property 9: Whitelist Path Bypass
*For any* request to a whitelisted path, the system should skip JWT validation entirely.
**Validates: Requirements 8.2**

## Error Handling

### Error Categories

1. **Missing Token** (401)
   - Message: "Missing authentication token"
   - Action: Return 401 immediately

2. **Revoked Token** (401)
   - Message: "Token has been revoked"
   - Action: Check blacklist, return 401 if found

3. **Expired Token** (401)
   - Message: "Token has expired"
   - Action: Log expiration time, return 401
   - Metric: `jwt.validation.expired`

4. **Invalid Signature** (401)
   - Message: "Invalid or expired token"
   - Action: Log detailed error, return 401
   - Metric: `jwt.validation.failure` with reason

5. **Malformed Token** (401)
   - Message: "Invalid or expired token"
   - Action: Log parsing error, return 401
   - Metric: `jwt.validation.failure` with reason

6. **Unexpected Error** (401)
   - Message: "Authentication failed"
   - Action: Log full stack trace, return 401
   - Metric: `jwt.validation.failure` with reason

### Error Response Format

```json
{
  "code": 401,
  "message": "Invalid or expired token",
  "data": null
}
```

### Logging Strategy

**Success**:
```
DEBUG: Token validated successfully - userId: {userId}, cached: {true/false}
```

**Failure**:
```
ERROR: Token validation failed - token: {masked}, error: {message}, stackTrace: {trace}
```

**Expired**:
```
WARN: Token expired - userId: {userId}, expiration: {timestamp}
```

## Testing Strategy

### Unit Tests

1. **JwtTokenValidator Tests**
   - Test valid token validation
   - Test expired token handling
   - Test invalid signature handling
   - Test malformed token handling
   - Test thread safety with concurrent validations
   - Test JwtParser singleton reuse

2. **TokenValidationCache Tests**
   - Test cache hit/miss
   - Test cache expiration
   - Test cache eviction (LRU)
   - Test cache statistics
   - Test ThreadLocal MessageDigest isolation
   - Test cache disabled scenario (enabled: false)
   - Test cache operations when disabled (should not throw exceptions)

3. **JwtMetricsCollector Tests**
   - Test metric recording
   - Test counter increments
   - Test timer recording

### Property-Based Tests

1. **Property 1: Thread Safety**
   - Generate random valid tokens
   - Validate concurrently from multiple threads
   - Assert all validations succeed

2. **Property 2: JwtParser Singleton**
   - Mock JwtParser creation
   - Assert only one instance created during application lifecycle

3. **Property 3: Cache Consistency**
   - Generate random valid token
   - Validate multiple times
   - Assert same result returned

4. **Property 4: Validation Correctness**
   - Generate random token with claims
   - Validate token
   - Assert extracted claims match

### Integration Tests

1. **Load Test with 500 Concurrent Users**
   - Run JMeter test with 500 concurrent requests
   - Assert JWT validation success rate > 99.9%
   - Assert no signature mismatch errors
   - Assert average validation time < 10ms

2. **Cache Performance Test**
   - Send 1000 requests with same token
   - Assert cache hit rate > 99%
   - Assert validation time decreases after first request

### Test Configuration

**Minimum 100 iterations per property test**

**Property Test Tags**:
- Feature: jwt-concurrent-validation, Property 1: Thread Safety of JWT Validation
- Feature: jwt-concurrent-validation, Property 2: JwtParser Singleton Reuse
- Feature: jwt-concurrent-validation, Property 3: Cache Consistency
- Feature: jwt-concurrent-validation, Property 4: Cache Expiration
- Feature: jwt-concurrent-validation, Property 5: Validation Result Correctness
- Feature: jwt-concurrent-validation, Property 6: Error Handling Completeness
- Feature: jwt-concurrent-validation, Property 7: Metrics Accuracy
- Feature: jwt-concurrent-validation, Property 8: Blacklist Check Before Validation
- Feature: jwt-concurrent-validation, Property 9: Whitelist Path Bypass

### Monitoring and Alerting

**Grafana Dashboard Metrics**:
- JWT validation success rate (%)
- JWT validation failure rate (%)
- JWT cache hit rate (%)
- JWT validation latency (p50, p95, p99)
- JWT validation throughput (req/s)

**Alert Rules**:
- JWT validation failure rate > 1% for 5 minutes
- JWT validation latency p99 > 100ms for 5 minutes
- JWT cache hit rate < 80% for 10 minutes

## Dependencies

### New Dependencies

```xml
<!-- Caffeine Cache -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Micrometer (already included in Spring Boot Actuator) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
```

### Configuration

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET:your-secret-key-here-must-be-at-least-256-bits}
  access-token-expiration: 7200  # 2 hours
  refresh-token-expiration: 604800  # 7 days
  whitelist:
    - /api/v1/auth/login
    - /api/v1/auth/register
    - /api/v1/auth/refresh
    - /api/v1/posts
    - /api/v1/posts/*
    - /api/v1/users/*/profile
    - /api/v1/search/**
    - /api/v1/ranking/**
    - /actuator/**
    - /api/v1/leaf/**
  
  # Cache configuration
  cache:
    enabled: true      # Enable/disable cache (for troubleshooting)
    max-size: 10000    # Maximum number of cached tokens
    ttl-minutes: 5     # Cache TTL in minutes

# Actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ZhiCore-gateway
```

## Performance Considerations

### Before Optimization

- **Validation Time**: ~5-10ms per request
- **Object Creation**: New SecretKey + JwtParser per request
- **Cache Hit Rate**: 0% (no cache)
- **GC Pressure**: High (many temporary objects)
- **Concurrent Validation**: Works but inefficient

### After Optimization

- **Validation Time**: 
  - Cache Hit: ~0.1ms
  - Cache Miss: ~5-10ms
- **Object Creation**: Zero per request (singleton reuse)
- **Cache Hit Rate**: ~95% (for repeated tokens)
- **GC Pressure**: Low (no temporary objects)
- **Concurrent Validation**: Thread-safe and efficient
- **Memory Usage**: ~10MB for 10,000 cached tokens

### Comparison Table

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| SecretKey Creation | Per request | Once at startup | 100% reduction |
| JwtParser Creation | Per request | Once at startup | 100% reduction |
| Avg Validation Time | ~5-10ms | ~0.1ms (cached) | 50-100x faster |
| GC Pressure | High | Low | Significant |
| Thread Safety | Safe (no sharing) | Safe (immutable) | Same |

### Scalability

- **Horizontal Scaling**: Each gateway instance has its own cache
- **Cache Warming**: No pre-warming needed, cache builds naturally
- **Cache Invalidation**: Automatic via TTL, manual via blacklist
- **Cache Toggle**: Can be disabled via configuration for troubleshooting without code changes

### Cache Configuration Scenarios

| Scenario | Configuration | Use Case |
|----------|--------------|----------|
| Production (Normal) | `enabled: true, maxSize: 10000, ttl: 5` | High performance with caching |
| Troubleshooting | `enabled: false` | Disable cache to isolate issues |
| Low Memory | `enabled: true, maxSize: 1000, ttl: 2` | Reduce memory footprint |
| High Security | `enabled: true, maxSize: 5000, ttl: 1` | Shorter cache duration |
| Development | `enabled: false` | Easier debugging without cache |

## Security Considerations

1. **Token Hashing**: Tokens are hashed (SHA-256) before storing in cache to prevent memory dumps from exposing tokens
2. **Secret Key Protection**: Secret key is pre-computed once and stored as immutable field (not re-read from config)
3. **Logging**: Tokens are masked in logs (show first 10 and last 10 chars only)
4. **Cache TTL**: Short TTL (5 minutes) ensures revoked tokens don't stay cached long
5. **Blacklist Check**: Always check blacklist before cache to ensure revoked tokens are rejected

## Migration Plan

### Phase 1: Implement Core Components (Day 1)
1. Create `JwtTokenValidator` with pre-built JwtParser
2. Create `TokenValidationCache` with ThreadLocal MessageDigest
3. Create `ValidationResult`
4. Create `JwtMetricsCollector`
5. Create `CacheStats`

### Phase 2: Update Filter (Day 1)
1. Update `JwtAuthenticationFilter` to use new validator
2. Add dependency injection
3. Update error handling

### Phase 3: Update Other JWT Implementations (Day 1)
1. Update `JwtTokenProvider` in ZhiCore-user to pre-build JwtParser
2. Update `UserContextFilter` in ZhiCore-common to pre-build JwtParser

### Phase 4: Testing (Day 2)
1. Write unit tests
2. Write property-based tests
3. Run integration tests
4. Run load tests (500 concurrent)

### Phase 5: Monitoring (Day 2)
1. Add Grafana dashboard
2. Configure alerts
3. Test alert triggers

### Phase 6: Deployment (Day 3)
1. Deploy to staging
2. Run smoke tests
3. Monitor metrics
4. Deploy to production
5. Monitor production metrics

## Rollback Plan

If issues occur after deployment:

1. **Immediate Rollback**: Revert to previous version
2. **Disable Cache**: Set `jwt.cache.enabled: false` via configuration (no code change needed)
3. **Increase Logging**: Enable debug logging for JWT validation
4. **Monitor Metrics**: Watch for signature mismatch errors

### Disabling Cache for Troubleshooting

If you suspect cache-related issues:

```yaml
# application.yml or via environment variable
jwt:
  cache:
    enabled: false  # Disable cache temporarily
```

Or via environment variable:
```bash
JWT_CACHE_ENABLED=false
```

This allows you to quickly disable caching without code changes or redeployment.

## Success Criteria

1. **Zero signature mismatch errors** in 500 concurrent load test
2. **JWT validation success rate > 99.9%**
3. **Cache hit rate > 90%** after warm-up
4. **Average validation time < 10ms**
5. **P99 validation time < 50ms**
6. **No memory leaks** after 24 hours of operation
7. **Zero JwtParser/SecretKey objects created after startup** (verified via JVM profiling)
