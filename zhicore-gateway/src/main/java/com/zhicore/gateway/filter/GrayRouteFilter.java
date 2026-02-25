package com.zhicore.gateway.filter;

import com.zhicore.gateway.config.GrayReleaseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 灰度路由过滤器
 * 
 * 根据配置的灰度策略，将部分流量路由到灰度版本服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayRouteFilter implements GlobalFilter, Ordered {

    private static final String GRAY_VERSION_HEADER = "X-Gray-Version";
    private static final String GRAY_TAG = "gray";

    private final GrayReleaseProperties grayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!grayProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();

        // 检查是否已有灰度标记
        String grayVersion = request.getHeaders().getFirst(GRAY_VERSION_HEADER);
        if (StringUtils.hasText(grayVersion)) {
            return chain.filter(exchange);
        }

        // 判断是否应该路由到灰度版本
        boolean shouldGray = shouldRouteToGray(request);

        if (shouldGray) {
            log.debug("Routing request to gray version: {}", request.getPath());
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(GRAY_VERSION_HEADER, GRAY_TAG)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }

    /**
     * 判断是否应该路由到灰度版本
     */
    private boolean shouldRouteToGray(ServerHttpRequest request) {
        // 策略1：基于用户ID的灰度
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (StringUtils.hasText(userId) && grayProperties.getGrayUserIds().contains(userId)) {
            return true;
        }

        // 策略2：基于百分比的灰度
        if (grayProperties.getPercentage() > 0) {
            // 使用请求的某个稳定特征（如用户ID或IP）来决定
            String identifier = userId != null ? userId : 
                    request.getRemoteAddress() != null ? 
                            request.getRemoteAddress().getAddress().getHostAddress() : 
                            String.valueOf(System.nanoTime());
            
            int hash = Math.abs(identifier.hashCode());
            return (hash % 100) < grayProperties.getPercentage();
        }

        return false;
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
