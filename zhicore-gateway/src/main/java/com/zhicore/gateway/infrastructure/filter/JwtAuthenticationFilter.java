package com.zhicore.gateway.infrastructure.filter;

import com.zhicore.gateway.application.model.ValidationResult;
import com.zhicore.gateway.application.service.JwtTokenValidator;
import com.zhicore.gateway.application.service.TokenBlacklistService;
import com.zhicore.gateway.config.JwtProperties;
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

/**
 * JWT 认证过滤器
 * 
 * 职责：
 * - 检查白名单路径，跳过认证
 * - 提取 Authorization Header 中的 JWT Token
 * - 检查 Token 黑名单
 * - 使用线程安全的验证器验证 Token（支持缓存）
 * - 将用户信息添加到请求头，传递给下游服务
 *
 * @author ZhiCore Team
 */
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
    private final JwtTokenValidator tokenValidator;  // 注入线程安全的验证器
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // OPTIONS 预检请求直接放行（CORS 支持）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        // 检查是否为白名单路径
        if (isWhitelistPath(path)) {
            return chain.filter(exchange);
        }

        // 获取 Token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "Missing authentication token");
        }

        // 检查 Token 是否在黑名单中
        return tokenBlacklistService.isBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        return unauthorized(exchange, "Token has been revoked");
                    }
                    return validateAndForward(exchange, chain, token);
                });
    }

    private Mono<Void> validateAndForward(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        // 使用线程安全的验证器（支持缓存）
        return Mono.fromCallable(() -> tokenValidator.validate(token))
                .flatMap(resultOpt -> {
                    if (resultOpt.isEmpty()) {
                        log.warn("Token validation failed or expired");
                        return unauthorized(exchange, "Invalid or expired token");
                    }
                    
                    ValidationResult result = resultOpt.get();
                    
                    // 将用户信息添加到请求头
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(USER_ID_HEADER, result.getUserId())
                            .header(USER_NAME_HEADER, result.getUserName() != null ? result.getUserName() : "")
                            .header(USER_ROLES_HEADER, result.getRoles() != null ? result.getRoles() : "")
                            .build();

                    log.debug("Token validated successfully - userId: {}", result.getUserId());
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
