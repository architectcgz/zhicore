package com.blog.gateway.security;

import com.blog.gateway.config.JwtProperties;
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

/**
 * JWT Token 验证器（线程安全）
 * 
 * 核心优化：
 * 预创建不可变的 SecretKey 单例（启动时创建一次）
 * 预构建不可变的 JwtParser 单例（线程安全，可复用
 * 使用 Caffeine 本地缓存避免重复解析
 * 详细的指标收集和日志记录
 * @author Blog Team
 */
@Slf4j
@Component
public class JwtTokenValidator {
    
    // 预计算的不可变 SecretKey（单例）
    private final SecretKey secretKey;
    
    // 预构建的不可变 JwtParser（线程安全，单例）
    private final JwtParser jwtParser;
    
    private final TokenValidationCache validationCache;
    private final JwtMetricsCollector metricsCollector;
    
    public JwtTokenValidator(JwtProperties jwtProperties,
                            TokenValidationCache validationCache,
                            JwtMetricsCollector metricsCollector) {
        // 启动时预计算密钥（不可变）
        this.secretKey = Keys.hmacShaKeyFor(
            jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
        
        // [关键] 启动时预构建 JwtParser（不可变，线程安全）
        // 根据 JJWT 官方文档，通过 builder 创建的 JwtParser 具有以下特性：
        // 1. 不可变 - build() 后无法修改
        // 2. 线程安全 - 可以安全地在多个线程间共享
        // 3. 可复用 - 应该复用以避免对象创建开销
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
        
        this.validationCache = validationCache;
        this.metricsCollector = metricsCollector;
        
        log.info("JwtTokenValidator initialized with pre-built JwtParser (thread-safe singleton)");
    }
    
    /**
     * 验证 JWT Token（支持缓存）
     * 
     * 线程安全方法
     * 
     * @param token JWT Token
     * @return 验证结果（包含用户信息），验证失败返回 empty
     */
    public Optional<ValidationResult> validate(String token) {
        long startTime = System.nanoTime();
        
        try {
            // 首先检查缓存
            Optional<ValidationResult> cached = validationCache.get(token);
            if (cached.isPresent()) {
                metricsCollector.recordCacheHit();
                metricsCollector.recordValidationTime(System.nanoTime() - startTime);
                log.debug("Token validation cache hit - userId: {}", cached.get().getUserId());
                return cached;
            }
            
            // 缓存未命中 - 执行验证
            metricsCollector.recordCacheMiss();
            
            //  复用单例JwtParser 解析
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            
            // 提取用户信息
            ValidationResult result = ValidationResult.builder()
                    .userId(claims.getSubject())
                    .userName(claims.get("userName", String.class))
                    .roles(claims.get("roles", String.class))
                    .build();
            
            // 缓存结果
            validationCache.put(token, result);
            
            metricsCollector.recordSuccess();
            metricsCollector.recordValidationTime(System.nanoTime() - startTime);
            
            log.debug("Token validated successfully - userId: {}, cached: false", result.getUserId());
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
     * 脱敏 Token 用于日志记录（显示前 10 和后 10 个字符）
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 20) {
            return "***";
        }
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }
}
