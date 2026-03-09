package com.zhicore.notification.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.notification.infrastructure.sentinel.NotificationRoutes;
import com.zhicore.notification.infrastructure.sentinel.NotificationSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NotificationSentinelConfig 测试")
class NotificationSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载通知服务 URL 与方法级规则")
    void shouldLoadNotificationRules() {
        NotificationSentinelProperties properties = new NotificationSentinelProperties();
        NotificationSentinelConfig config = new NotificationSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> NotificationRoutes.PREFIX.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> NotificationRoutes.UNREAD_COUNT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> NotificationSentinelResources.GET_AGGREGATED_NOTIFICATIONS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> NotificationSentinelResources.GET_UNREAD_COUNT.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        NotificationSentinelProperties properties = new NotificationSentinelProperties();
        NotificationSentinelConfig config = new NotificationSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long aggregatedRules = FlowRuleManager.getRules().stream()
                .filter(rule -> NotificationSentinelResources.GET_AGGREGATED_NOTIFICATIONS.equals(rule.getResource()))
                .count();

        assertEquals(1L, aggregatedRules);
    }
}
