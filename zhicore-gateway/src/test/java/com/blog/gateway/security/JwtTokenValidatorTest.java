package com.zhicore.gateway.security;

import com.zhicore.gateway.config.JwtProperties;
import com.zhicore.gateway.service.store.TokenValidationStore;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JwtTokenValidator 单元测试
 * 测试有效 Token、过期 Token、无效签名、畸形 Token 的处理
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenValidatorTest {

    @Mock
    private TokenValidationStore mockStore;

    @Mock
    private JwtMetricsCollector mockMetrics;

    private JwtTokenValidator validator;
    private SecretKey secretKey;
    private String testSecret = "test-secret-key-for-jwt-validation-must-be-at-least-256-bits-long";

    @BeforeEach
    void setUp() {
        // 创建测试用的 JwtProperties
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(testSecret);

        // 创建密钥
        secretKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));

        // 模拟缓存未命中
        when(mockStore.get(any())).thenReturn(Optional.empty());

        // 创建验证器
        validator = new JwtTokenValidator(jwtProperties, mockStore, mockMetrics);
    }

    @Test
    void testValidToken() {
        // 创建有效的 Token
        String token = createValidToken("user123", "testuser", "ROLE_USER", 3600);

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(token);

        // 断言
        assertTrue(result.isPresent());
        assertEquals("user123", result.get().getUserId());
        assertEquals("testuser", result.get().getUserName());
        assertEquals("ROLE_USER", result.get().getRoles());

        // 验证指标记录
        verify(mockMetrics).recordSuccess();
        verify(mockMetrics).recordCacheMiss();
        verify(mockMetrics).recordValidationTime(anyLong());
        verify(mockStore).put(eq(token), any(ValidationResult.class));
    }

    @Test
    void testExpiredToken() {
        // 创建已过期的 Token（过期时间为 -1 秒）
        String token = createValidToken("user456", "expireduser", "ROLE_USER", -1);

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(token);

        // 断言
        assertFalse(result.isPresent());

        // 验证指标记录
        verify(mockMetrics).recordExpired();
        verify(mockMetrics).recordValidationTime(anyLong());
        verify(mockStore, never()).put(any(), any());
    }

    @Test
    void testInvalidSignature() {
        // 使用不同的密钥创建 Token
        SecretKey wrongKey = Keys.hmacShaKeyFor(
            "wrong-secret-key-for-jwt-validation-must-be-at-least-256-bits-long".getBytes(StandardCharsets.UTF_8)
        );

        String token = Jwts.builder()
                .subject("user789")
                .claim("userName", "wronguser")
                .claim("roles", "ROLE_USER")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(wrongKey)
                .compact();

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(token);

        // 断言
        assertFalse(result.isPresent());

        // 验证指标记录
        verify(mockMetrics).recordFailure(anyString());
        verify(mockMetrics).recordValidationTime(anyLong());
        verify(mockStore, never()).put(any(), any());
    }

    @Test
    void testMalformedToken() {
        // 测试畸形 Token
        String malformedToken = "this.is.not.a.valid.jwt.token";

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(malformedToken);

        // 断言
        assertFalse(result.isPresent());

        // 验证指标记录
        verify(mockMetrics).recordFailure(anyString());
        verify(mockMetrics).recordValidationTime(anyLong());
    }

    @Test
    void testEmptyToken() {
        // 测试空 Token
        String emptyToken = "";

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(emptyToken);

        // 断言
        assertFalse(result.isPresent());

        // 验证指标记录
        verify(mockMetrics).recordFailure(anyString());
    }

    @Test
    void testNullClaims() {
        // 创建没有自定义 claims 的 Token
        String token = Jwts.builder()
                .subject("user999")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(secretKey)
                .compact();

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(token);

        // 断言
        assertTrue(result.isPresent());
        assertEquals("user999", result.get().getUserId());
        assertNull(result.get().getUserName());
        assertNull(result.get().getRoles());
    }

    @Test
    void testCacheHit() {
        // 创建有效的 Token
        String token = createValidToken("user111", "cacheduser", "ROLE_ADMIN", 3600);

        // 模拟缓存命中
        ValidationResult cachedResult = ValidationResult.builder()
                .userId("user111")
                .userName("cacheduser")
                .roles("ROLE_ADMIN")
                .build();
        when(mockStore.get(token)).thenReturn(Optional.of(cachedResult));

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(token);

        // 断言
        assertTrue(result.isPresent());
        assertEquals("user111", result.get().getUserId());

        // 验证缓存命中指标
        verify(mockMetrics).recordCacheHit();
        verify(mockMetrics).recordValidationTime(anyLong());
        verify(mockMetrics, never()).recordSuccess();
        verify(mockStore, never()).put(any(), any());
    }

    @Test
    void testMultipleRoles() {
        // 创建包含多个角色的 Token
        String token = createValidToken("user222", "adminuser", "ROLE_USER,ROLE_ADMIN", 3600);

        // 验证 Token
        Optional<ValidationResult> result = validator.validate(token);

        // 断言
        assertTrue(result.isPresent());
        assertEquals("ROLE_USER,ROLE_ADMIN", result.get().getRoles());
    }

    /**
     * 辅助方法：创建有效的 JWT Token
     */
    private String createValidToken(String userId, String userName, String roles, int expirationSeconds) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationSeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .subject(userId)
                .claim("userName", userName)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }
}
