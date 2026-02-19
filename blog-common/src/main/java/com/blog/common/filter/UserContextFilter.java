package com.blog.common.filter;

import com.blog.common.constant.CommonConstants;
import com.blog.common.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 用户上下文过滤器
 * 从请求头中读取用户信息并设置到 UserContext
 * 支持两种方式：
 * 1. 从网关转发的 X-User-Id 头读取
 * 2. 直接解析 JWT Token（用于直接调用服务的场景）
 * 
 * 优化：预创建不可变的 SecretKey 和 JwtParser 单例，避免重复创建
 * 
 * @author Blog Team
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UserContextFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String jwtSecret;

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
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        
        // 预构建 JwtParser（不可变，线程安全）
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
        
        log.info("UserContextFilter initialized with pre-built SecretKey and JwtParser");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 优先从请求头中读取用户信息（由网关设置）
            String userId = request.getHeader(CommonConstants.HEADER_USER_ID);
            String userName = request.getHeader(CommonConstants.HEADER_USER_NAME);

            // 如果没有 X-User-Id 头，尝试从 JWT Token 解析
            if (!StringUtils.hasText(userId)) {
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
                    String token = authHeader.substring(BEARER_PREFIX.length());
                    try {
                        Claims claims = parseToken(token);
                        userId = claims.getSubject();
                        userName = claims.get("userName", String.class);
                        log.debug("UserContext from JWT: userId={}, userName={}", userId, userName);
                    } catch (Exception e) {
                        log.debug("Failed to parse JWT token: {}", e.getMessage());
                    }
                }
            }

            if (StringUtils.hasText(userId)) {
                UserContext.UserInfo userInfo = new UserContext.UserInfo(userId, userName);
                UserContext.setUser(userInfo);
                log.debug("UserContext set: userId={}, userName={}", userId, userName);
            }

            filterChain.doFilter(request, response);
        } finally {
            // 清理 ThreadLocal，防止内存泄漏
            UserContext.clear();
        }
    }

    private Claims parseToken(String token) {
        // 使用预构建的 JwtParser（线程安全）
        return jwtParser.parseSignedClaims(token).getPayload();
    }
}
