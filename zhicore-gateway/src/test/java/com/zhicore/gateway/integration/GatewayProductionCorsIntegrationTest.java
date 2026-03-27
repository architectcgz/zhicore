package com.zhicore.gateway.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=prod",
                "spring.config.import=",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.gateway.routes[0].id=public-auth-login-route",
                "spring.cloud.gateway.routes[0].uri=forward:/__test/public/auth/login",
                "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/auth/login",
                "jwt.whitelist[0]=/api/v1/auth/login"
        }
)
@DisplayName("Gateway 生产配置 CORS 集成测试")
class GatewayProductionCorsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("非 dev profile 下带 localhost Origin 的登录请求应允许通过")
    void loginEndpoint_withLocalhostOriginOutsideDevProfile_shouldAllowRequest() throws Exception {
        assertThat(environment.getActiveProfiles()).containsExactly("prod");
        assertThat(Binder.get(environment)
                .bind("cors.allowed-origin-patterns", Bindable.listOf(String.class))
                .orElse(List.of()))
                .isEqualTo(List.of("http://localhost:[*]", "http://127.0.0.1:[*]"));

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
}
