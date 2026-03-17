package com.zhicore.message.integration;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.message.MessageApplication;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.annotation.AnnotationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID生成器集成测试
 * 
 * 验证 ZhiCore-message 服务显式注册了 IdGeneratorFeignClient
 */
class IdGeneratorIntegrationTest {

    @Test
    void shouldRegisterIdGeneratorFeignClientOnApplication() {
        EnableFeignClients annotation = AnnotationUtils.findAnnotation(
                MessageApplication.class,
                EnableFeignClients.class
        );

        assertThat(annotation)
                .as("@EnableFeignClients should be present on MessageApplication")
                .isNotNull();
        assertThat(annotation.clients())
                .as("MessageApplication should explicitly register IdGeneratorFeignClient")
                .contains(IdGeneratorFeignClient.class);
    }
}
