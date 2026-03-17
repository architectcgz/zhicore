package com.zhicore.notification.integration;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.notification.NotificationApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.annotation.AnnotationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID生成服务集成测试
 * 
 * 验证 ZhiCore-notification 服务显式注册了 IdGeneratorFeignClient
 */
@DisplayName("ID生成服务集成测试")
class IdGeneratorIntegrationTest {

    @Test
    @DisplayName("应该在启动类上显式注册 IdGeneratorFeignClient")
    void shouldRegisterIdGeneratorFeignClientOnApplication() {
        EnableFeignClients annotation = AnnotationUtils.findAnnotation(
                NotificationApplication.class,
                EnableFeignClients.class
        );

        assertThat(annotation)
                .as("@EnableFeignClients should be present on NotificationApplication")
                .isNotNull();
        assertThat(annotation.clients())
                .as("NotificationApplication should explicitly register IdGeneratorFeignClient")
                .contains(IdGeneratorFeignClient.class);
    }
}
