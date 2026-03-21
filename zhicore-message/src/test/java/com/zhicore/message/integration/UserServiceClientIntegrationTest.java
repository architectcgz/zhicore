package com.zhicore.message.integration;

import com.zhicore.message.infrastructure.feign.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AnnotationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户服务客户端集成测试
 *
 * 验证消息服务为用户服务 FeignClient 保留 Docker 环境下的直连兜底地址。
 */
class UserServiceClientIntegrationTest {

    @Test
    void shouldKeepDirectUrlFallbackForUserServiceClient() {
        FeignClient annotation = AnnotationUtils.findAnnotation(UserServiceClient.class, FeignClient.class);

        assertThat(annotation)
                .as("@FeignClient should be present on UserServiceClient")
                .isNotNull();
        assertThat(annotation.name())
                .as("UserServiceClient should target zhicore-user")
                .isEqualTo("zhicore-user");
        assertThat(annotation.url())
                .as("UserServiceClient should keep a Docker-friendly direct URL fallback")
                .isEqualTo("${ZHICORE_USER_URL:http://zhicore-user:8081}");
    }
}
