package com.zhicore.comment.integration;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.comment.CommentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.annotation.AnnotationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID生成服务集成测试
 * 
 * 验证 ZhiCore-comment 服务显式注册了 IdGeneratorFeignClient
 */
class IdGeneratorIntegrationTest {

    @Test
    void shouldRegisterIdGeneratorFeignClientOnApplication() {
        EnableFeignClients annotation = AnnotationUtils.findAnnotation(
                CommentApplication.class,
                EnableFeignClients.class
        );

        assertThat(annotation)
                .as("@EnableFeignClients should be present on CommentApplication")
                .isNotNull();
        assertThat(annotation.clients())
                .as("CommentApplication should explicitly register IdGeneratorFeignClient")
                .contains(IdGeneratorFeignClient.class);
    }
}
