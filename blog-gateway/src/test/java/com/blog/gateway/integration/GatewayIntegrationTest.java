package com.blog.gateway.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway 集成测试
 * 
 * 测试 API Gateway 的路由、认证和限流功能
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Gateway 集成测试")
class GatewayIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("健康检查端点应返回 200")
    void healthEndpoint_shouldReturn200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/actuator/health", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("未认证请求访问受保护资源应返回 401")
    void protectedEndpoint_withoutToken_shouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/users/me", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("公开端点应允许匿名访问")
    void publicEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/posts/hot", String.class);
        
        // 即使下游服务不可用，也不应该是 401
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Prometheus 指标端点应可访问")
    void prometheusEndpoint_shouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/actuator/prometheus", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory");
    }
}
