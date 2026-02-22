package com.zhicore.user.infrastructure.security;

import com.zhicore.common.config.JwtProperties;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT Token 提供者
 * 
 * 优化：预创建不可变的 SecretKey 和 JwtParser 单例，避免重复创建
 * 使用 @ConfigurationProperties 支持配置动态刷新
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    // 预创建的不可变 SecretKey（单例）
    private SecretKey secretKey;
    
    // 预构建的不可变 JwtParser（线程安全，单例）
    private JwtParser jwtParser;

    /**
     * 初始化方法：预创建 SecretKey 和 JwtParser
     */
    @PostConstruct
    public void init() {
        // 预计算密钥（不可变）
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        
        // 预构建 JwtParser（不可变，线程安全）
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
        
        log.info("JwtTokenProvider initialized with pre-built SecretKey and JwtParser");
    }

    /**
     * 生成访问令牌
     *
     * @param user 用户
     * @return 访问令牌
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);

        String roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.joining(","));

        // 使用预创建的 SecretKey
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userName", user.getUserName())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成刷新令牌
     *
     * @param user 用户
     * @return 刷新令牌
     */
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration() * 1000);

        // 使用预创建的 SecretKey
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从Token获取用户ID
     *
     * @param token Token
     * @return 用户ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 验证Token
     *
     * @param token Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 获取访问令牌过期时间（秒）
     *
     * @return 过期时间
     */
    public Long getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    /**
     * 解析Token
     *
     * @param token Token
     * @return Claims
     */
    private Claims parseToken(String token) {
        // 使用预构建的 JwtParser（线程安全）
        return jwtParser.parseSignedClaims(token).getPayload();
    }
}
