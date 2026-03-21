package com.zhicore.admin.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.admin.application.sentinel.AdminSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AdminSentinelConfig 测试")
class AdminSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载管理服务热点查询规则")
    void shouldLoadAdminRules() {
        AdminSentinelProperties properties = new AdminSentinelProperties();
        AdminSentinelConfig config = new AdminSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> AdminSentinelResources.LIST_USERS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> AdminSentinelResources.LIST_POSTS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> AdminSentinelResources.LIST_COMMENTS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> AdminSentinelResources.LIST_REPORTS_BY_STATUS.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        AdminSentinelProperties properties = new AdminSentinelProperties();
        AdminSentinelConfig config = new AdminSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long rules = FlowRuleManager.getRules().stream()
                .filter(rule -> AdminSentinelResources.LIST_USERS.equals(rule.getResource()))
                .count();

        assertEquals(1L, rules);
    }

    @Test
    @DisplayName("相关配置变更后应该重新加载规则")
    void shouldReloadRulesWhenRelevantConfigurationChanges() {
        AdminSentinelProperties properties = new AdminSentinelProperties();
        AdminSentinelConfig config = new AdminSentinelConfig(properties);

        config.initFlowRules();
        properties.setUserListQps(1234);

        config.onEnvironmentChange(new EnvironmentChangeEvent(Set.of("admin.sentinel.user-list-qps")));

        assertEquals(1234.0, FlowRuleManager.getRules().stream()
                .filter(rule -> AdminSentinelResources.LIST_USERS.equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }

    @Test
    @DisplayName("RefreshScope 刷新后应该重新加载规则")
    void shouldReloadRulesWhenRefreshScopeRefreshed() {
        AdminSentinelProperties properties = new AdminSentinelProperties();
        AdminSentinelConfig config = new AdminSentinelConfig(properties);

        config.initFlowRules();
        properties.setUserListQps(4321);

        config.onRefreshScopeRefreshed();

        assertEquals(4321.0, FlowRuleManager.getRules().stream()
                .filter(rule -> AdminSentinelResources.LIST_USERS.equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }
}
