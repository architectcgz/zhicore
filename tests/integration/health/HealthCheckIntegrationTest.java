package com.zhicore.integration.health;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ZhiCore 微服务健康检查集成测试
 *
 * 测试所有微服务和基础设施的健康状态
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HealthCheckIntegrationTest {

    private static final TestRestTemplate restTemplate = new TestRestTemplate();

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    @BeforeAll
    static void setup() {
        System.out.println("========================================");
        System.out.println("ZhiCore 微服务健康检查测试");
        System.out.println("========================================");
        System.out.println();
    }

    @AfterAll
    static void tearDown() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("测试总结");
        System.out.println("========================================");
        System.out.println("总测试数: " + totalTests);
        System.out.println("通过: " + passedTests);
        System.out.println("失败: " + failedTests);
        System.out.println();

        if (failedTests == 0) {
            System.out.println("✓ 所有测试通过！");
        } else {
            System.out.println("✗ 有测试失败！");
        }
    }

    /**
     * 测试健康检查端点
     */
    private void testHealthEndpoint(String serviceName, String url) {
        totalTests++;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo("UP");

            passedTests++;
            System.out.println("✓ " + serviceName + ": Status UP");
        } catch (Exception e) {
            failedTests++;
            System.err.println("✗ " + serviceName + ": " + e.getMessage());
            throw new AssertionError(serviceName + " health check failed", e);
        }
    }

    // ========================================
    // 基础设施服务测试
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Elasticsearch 健康检查")
    void testElasticsearchHealth() {
        totalTests++;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:9200/_cluster/health", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            String status = (String) response.getBody().get("status");
            assertThat(status).isIn("green", "yellow");

            passedTests++;
            System.out.println("✓ Elasticsearch: Cluster health " + status);
        } catch (Exception e) {
            failedTests++;
            System.err.println("✗ Elasticsearch: " + e.getMessage());
            throw new AssertionError("Elasticsearch health check failed", e);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Nacos 健康检查")
    void testNacosHealth() {
        totalTests++;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8848/nacos/v1/console/health/readiness", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            passedTests++;
            System.out.println("✓ Nacos: Status UP");
        } catch (Exception e) {
            failedTests++;
            System.err.println("✗ Nacos: " + e.getMessage());
            throw new AssertionError("Nacos health check failed", e);
        }
    }

    // ========================================
    // 微服务健康检查
    // ========================================

    @Test
    @Order(10)
    @DisplayName("Gateway Service 健康检查")
    void testGatewayHealth() {
        testHealthEndpoint("Gateway Service", "http://localhost:8000/actuator/health");
    }

    @Test
    @Order(11)
    @DisplayName("User Service 健康检查")
    void testUserServiceHealth() {
        testHealthEndpoint("User Service", "http://localhost:8081/actuator/health");
    }

    @Test
    @Order(12)
    @DisplayName("Content Service 健康检查")
    void testContentServiceHealth() {
        testHealthEndpoint("Content Service", "http://localhost:8082/actuator/health");
    }

    @Test
    @Order(13)
    @DisplayName("Comment Service 健康检查")
    void testCommentServiceHealth() {
        testHealthEndpoint("Comment Service", "http://localhost:8083/actuator/health");
    }

    @Test
    @Order(14)
    @DisplayName("Message Service 健康检查")
    void testMessageServiceHealth() {
        testHealthEndpoint("Message Service", "http://localhost:8084/actuator/health");
    }

    @Test
    @Order(15)
    @DisplayName("Notification Service 健康检查")
    void testNotificationServiceHealth() {
        testHealthEndpoint("Notification Service", "http://localhost:8085/actuator/health");
    }

    @Test
    @Order(16)
    @DisplayName("Search Service 健康检查")
    void testSearchServiceHealth() {
        testHealthEndpoint("Search Service", "http://localhost:8086/actuator/health");
    }

    @Test
    @Order(17)
    @DisplayName("Ranking Service 健康检查")
    void testRankingServiceHealth() {
        testHealthEndpoint("Ranking Service", "http://localhost:8087/actuator/health");
    }

    @Test
    @Order(18)
    @DisplayName("ID Generator Service 健康检查")
    void testIdGeneratorServiceHealth() {
        testHealthEndpoint("ID Generator Service", "http://localhost:8088/actuator/health");
    }

    @Test
    @Order(19)
    @DisplayName("Upload Service 健康检查")
    void testUploadServiceHealth() {
        testHealthEndpoint("Upload Service", "http://localhost:8092/actuator/health");
    }

    @Test
    @Order(20)
    @DisplayName("Admin Service 健康检查")
    void testAdminServiceHealth() {
        testHealthEndpoint("Admin Service", "http://localhost:8093/actuator/health");
    }

    @Test
    @Order(21)
    @DisplayName("Ops Service 健康检查")
    void testOpsServiceHealth() {
        testHealthEndpoint("Ops Service", "http://localhost:8094/actuator/health");
    }
}
