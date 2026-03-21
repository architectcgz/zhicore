package com.zhicore.notification.integration;

import com.zhicore.notification.infrastructure.config.NotificationAggregationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.AnnotationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知聚合配置集成测试
 *
 * 验证通知聚合配置前缀符合 Spring Boot canonical name 规范。
 */
class NotificationAggregationPropertiesIntegrationTest {

    @Test
    void shouldUseCanonicalConfigurationPrefix() {
        ConfigurationProperties annotation = AnnotationUtils.findAnnotation(
                NotificationAggregationProperties.class,
                ConfigurationProperties.class
        );

        assertThat(annotation)
                .as("@ConfigurationProperties should be present on NotificationAggregationProperties")
                .isNotNull();
        assertThat(annotation.prefix())
                .as("Notification aggregation prefix should use lowercase canonical form")
                .isEqualTo("zhicore.notification.aggregation");
    }
}
