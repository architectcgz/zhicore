package com.zhicore.message.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.message.infrastructure.sentinel.MessageRoutes;
import com.zhicore.message.infrastructure.sentinel.MessageSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MessageSentinelConfig 测试")
class MessageSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载消息服务热点读接口规则")
    void shouldLoadMessageRules() {
        MessageSentinelProperties properties = new MessageSentinelProperties();
        MessageSentinelConfig config = new MessageSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> MessageRoutes.UNREAD_COUNT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> MessageSentinelResources.GET_CONVERSATION_LIST.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> MessageSentinelResources.GET_CONVERSATION.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> MessageSentinelResources.GET_MESSAGE_HISTORY.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> MessageSentinelResources.GET_UNREAD_COUNT.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        MessageSentinelProperties properties = new MessageSentinelProperties();
        MessageSentinelConfig config = new MessageSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long unreadRouteRules = FlowRuleManager.getRules().stream()
                .filter(rule -> MessageRoutes.UNREAD_COUNT.equals(rule.getResource()))
                .count();
        long rules = FlowRuleManager.getRules().stream()
                .filter(rule -> MessageSentinelResources.GET_CONVERSATION_LIST.equals(rule.getResource()))
                .count();

        assertEquals(1L, unreadRouteRules);
        assertEquals(1L, rules);
    }
}
