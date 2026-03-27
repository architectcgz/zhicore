package com.zhicore.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gateway 配置契约测试")
class GatewayConfigContractTest {

    private static final String UPLOAD_ROUTE_PATH = "Path=/api/v1/upload/**";
    private static final String FILE_ROUTE_PATH = "Path=/api/v1/files/**";
    private static final String MESSAGE_WEBSOCKET_ROUTE_PATH = "Path=/ws/message/**";
    private static final String NOTIFICATION_WEBSOCKET_ROUTE_PATH = "Path=/ws/notification/**";
    private static final String FILE_SERVICE_URI = "${FILE_SERVICE_URL:http://file-service-app:8089}";
    private static final String FILE_APP_HEADER_FILTER = "AddRequestHeader=X-App-Id, ${FILE_SERVICE_TENANT_ID:zhicore}";
    private static final List<String> REQUIRED_LOCAL_ORIGIN_PATTERNS = List.of(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]"
    );
    private static final List<String> REQUIRED_PUBLIC_PATHS = List.of(
            "GET:/api/v1/users/*",
            "GET:/api/v1/comments/*",
            "GET:/api/v1/comments/post/*/page",
            "GET:/api/v1/comments/*/like-count",
            "GET:/api/v1/comments/*/replies/page",
            "GET:/api/v1/files/*",
            "/ws/message/**",
            "/ws/notification/**"
    );

    private final Yaml yaml = new Yaml();

    @Test
    @DisplayName("主配置与 Nacos 配置都应将文件接口和 WebSocket 握手路由到正确服务，并放开公开白名单")
    void gatewayYamlFilesShouldExposePublicReadEndpointsAndUploadRoute() throws IOException {
        assertGatewayConfig(loadMainGatewayConfig());
        assertGatewayConfig(loadExternalGatewayConfig());
    }

    @Test
    @DisplayName("JwtProperties 默认白名单应覆盖公开读接口")
    void jwtPropertiesDefaultsShouldExposePublicReadEndpoints() {
        JwtProperties properties = new JwtProperties();

        assertThat(properties.getWhitelist()).contains(REQUIRED_PUBLIC_PATHS.toArray(String[]::new));
    }

    @Test
    @DisplayName("主配置与 Nacos 配置都应显式放开本地联调来源")
    void gatewayYamlShouldAllowLocalOriginsOutsideDevProfile() throws IOException {
        assertGatewayCorsConfig(loadMainGatewayConfig());
        assertGatewayCorsConfig(loadExternalGatewayConfig());
    }

    private Map<String, Object> loadMainGatewayConfig() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/application.yml")) {
            if (inputStream == null) {
                throw new IOException("classpath application.yml not found");
            }
            return yaml.load(inputStream);
        }
    }

    private Map<String, Object> loadExternalGatewayConfig() throws IOException {
        Path configPath = resolveRepoFile("config/nacos/zhicore-gateway.yml");
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            return yaml.load(inputStream);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertGatewayConfig(Map<String, Object> config) {
        Map<String, Object> spring = (Map<String, Object>) config.get("spring");
        Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
        Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
        List<Map<String, Object>> routes = (List<Map<String, Object>>) gateway.get("routes");

        Map<String, Object> uploadRoute = routes.stream()
                .filter(route -> ((List<String>) route.get("predicates")).contains(UPLOAD_ROUTE_PATH))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing upload route"));

        assertThat(uploadRoute.get("uri")).isEqualTo("lb://zhicore-upload");

        Map<String, Object> fileRoute = routes.stream()
                .filter(route -> ((List<String>) route.get("predicates")).contains(FILE_ROUTE_PATH))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing files route"));

        assertThat(fileRoute.get("uri")).isEqualTo(FILE_SERVICE_URI);
        assertThat((List<String>) fileRoute.get("filters")).contains(FILE_APP_HEADER_FILTER);

        Map<String, Object> messageWebSocketRoute = routes.stream()
                .filter(route -> ((List<String>) route.get("predicates")).contains(MESSAGE_WEBSOCKET_ROUTE_PATH))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing message websocket route"));

        assertThat(messageWebSocketRoute.get("uri")).isEqualTo("lb:ws://zhicore-message");

        Map<String, Object> notificationWebSocketRoute = routes.stream()
                .filter(route -> ((List<String>) route.get("predicates")).contains(NOTIFICATION_WEBSOCKET_ROUTE_PATH))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing notification websocket route"));

        assertThat(notificationWebSocketRoute.get("uri")).isEqualTo("lb:ws://zhicore-notification");

        Map<String, Object> jwt = (Map<String, Object>) config.get("jwt");
        List<String> whitelist = (List<String>) jwt.get("whitelist");

        assertThat(whitelist).contains(REQUIRED_PUBLIC_PATHS.toArray(String[]::new));
    }

    @SuppressWarnings("unchecked")
    private void assertGatewayCorsConfig(Map<String, Object> config) {
        Map<String, Object> cors = (Map<String, Object>) config.get("cors");

        assertThat(cors)
                .as("gateway config should define cors settings for local browser flows")
                .isNotNull();

        List<String> allowedOriginPatterns = (List<String>) cors.get("allowed-origin-patterns");

        assertThat(allowedOriginPatterns)
                .contains(REQUIRED_LOCAL_ORIGIN_PATTERNS.toArray(String[]::new));
    }

    private Path resolveRepoFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("unable to resolve repo file: " + relativePath);
    }
}
