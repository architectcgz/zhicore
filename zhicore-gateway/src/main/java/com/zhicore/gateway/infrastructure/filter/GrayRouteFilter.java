package com.zhicore.gateway.infrastructure.filter;

import com.zhicore.gateway.application.service.GrayRouteService;
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

    private final GrayRouteService grayRouteService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
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
        String userId = request.getHeaders().getFirst("X-User-Id");
        String clientAddress = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;
        return grayRouteService.shouldRouteToGray(userId, clientAddress, String.valueOf(System.nanoTime()));
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
