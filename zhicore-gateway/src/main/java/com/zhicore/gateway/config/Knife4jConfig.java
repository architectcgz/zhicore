package com.zhicore.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Knife4j 网关配置
 * 
 * 配置 Knife4j 文档聚合和静态资源访问
 * 
 * @author ZhiCore Team
 */
@Configuration
public class Knife4jConfig {

    /**
     * 配置 Knife4j 静态资源过滤器
     * 允许访问 doc.html 和相关静态资源
     */
    @Bean
    public WebFilter knife4jWebFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            
            // 允许访问 Knife4j 文档页面和静态资源
            if (path.equals("/doc.html") || 
                path.startsWith("/webjars/") || 
                path.startsWith("/swagger-resources") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/v2/api-docs")) {
                
                ServerHttpResponse response = exchange.getResponse();
                HttpHeaders headers = response.getHeaders();
                
                // 设置 CORS 头
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
                headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
                
                // 处理 OPTIONS 预检请求
                if (request.getMethod() == HttpMethod.OPTIONS) {
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            }
            
            return chain.filter(exchange);
        };
    }
}
