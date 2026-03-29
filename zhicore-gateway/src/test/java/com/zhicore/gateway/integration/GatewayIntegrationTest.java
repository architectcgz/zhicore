package com.zhicore.gateway.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
            "/api/v1/private/users/me", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("公开端点应允许匿名访问")
    void publicEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/posts/hot", String.class);
        
        // 即使下游服务不可用，也不应该是 401
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("带 localhost Origin 的登录请求应允许通过")
    void loginEndpoint_withLocalhostOrigin_shouldAllowRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(restTemplate.getRootUri() + "/api/v1/auth/login"))
                .header("Origin", "http://localhost:3000")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"author@example.com","password":"Test123456!"}
                        """))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).contains("login-ok");
    }

    @Test
    @DisplayName("用户公开资料接口应允许匿名访问")
    void publicUserProfileEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/users/123456789", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("user-detail");
    }

    @Test
    @DisplayName("用户公开文章列表接口应允许匿名访问")
    void publicUserPostsEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/users/123456789/posts?page=1&size=20", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("user-posts");
    }

    @Test
    @DisplayName("评论详情接口应允许匿名访问")
    void publicCommentDetailEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/comments/123456789", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("comment-detail");
    }

    @Test
    @DisplayName("评论分页接口应允许匿名访问")
    void publicCommentListEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/comments/post/123456789/page?page=0&size=20", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("comment-list");
    }

    @Test
    @DisplayName("评论点赞数接口应允许匿名访问")
    void publicCommentLikeCountEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/comments/123456789/like-count", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("comment-like-count");
    }

    @Test
    @DisplayName("评论回复分页接口应允许匿名访问")
    void publicCommentRepliesPageEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/comments/123456789/replies/page?page=0&size=20", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("comment-replies-page");
    }

    @Test
    @DisplayName("评论删除接口不应因公开读白名单而放开")
    void deleteCommentEndpoint_withoutToken_shouldStillReturn401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(restTemplate.getRootUri() + "/api/v1/comments/123456789"))
                .DELETE()
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("文件详情接口应允许匿名访问")
    void publicFileDetailEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/files/test-file-id", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("file-detail:zhicore");
    }

    @Test
    @DisplayName("消息 WebSocket SockJS info 端点应允许匿名探活")
    void messageWebSocketInfoEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ws/message/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"message\"");
    }

    @Test
    @DisplayName("通知 WebSocket SockJS info 端点应允许匿名探活")
    void notificationWebSocketInfoEndpoint_shouldAllowAnonymousAccess() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ws/notification/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"service\":\"notification\"");
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
