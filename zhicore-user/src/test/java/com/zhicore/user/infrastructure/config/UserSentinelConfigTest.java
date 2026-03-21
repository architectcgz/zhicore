package com.zhicore.user.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.user.application.sentinel.UserSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UserSentinelConfig 测试")
class UserSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载用户服务热点读接口规则")
    void shouldLoadUserRules() {
        UserSentinelProperties properties = new UserSentinelProperties();
        UserSentinelConfig config = new UserSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_USER_DETAIL.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_USER_SIMPLE.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.BATCH_GET_USERS_SIMPLE.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_STRANGER_MESSAGE_SETTING.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_FOLLOWERS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_FOLLOWINGS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_FOLLOW_STATS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.IS_FOLLOWING.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_CHECK_IN_STATS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_MONTHLY_CHECK_IN_BITMAP.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.GET_BLOCKED_USERS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.IS_BLOCKED.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UserSentinelResources.QUERY_USERS.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        UserSentinelProperties properties = new UserSentinelProperties();
        UserSentinelConfig config = new UserSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long detailRules = FlowRuleManager.getRules().stream()
                .filter(rule -> UserSentinelResources.GET_USER_DETAIL.equals(rule.getResource()))
                .count();

        assertEquals(1L, detailRules);
    }

    @Test
    @DisplayName("相关配置变更后应该重新加载规则")
    void shouldReloadRulesWhenRelevantConfigurationChanges() {
        UserSentinelProperties properties = new UserSentinelProperties();
        UserSentinelConfig config = new UserSentinelConfig(properties);

        config.initFlowRules();
        properties.setUserDetailQps(1234);

        config.onEnvironmentChange(new EnvironmentChangeEvent(Set.of("user.sentinel.user-detail-qps")));

        assertEquals(1234.0, FlowRuleManager.getRules().stream()
                .filter(rule -> UserSentinelResources.GET_USER_DETAIL.equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }

    @Test
    @DisplayName("RefreshScope 刷新后应该重新加载规则")
    void shouldReloadRulesWhenRefreshScopeRefreshed() {
        UserSentinelProperties properties = new UserSentinelProperties();
        UserSentinelConfig config = new UserSentinelConfig(properties);

        config.initFlowRules();
        properties.setUserDetailQps(4321);

        config.onRefreshScopeRefreshed();

        assertEquals(4321.0, FlowRuleManager.getRules().stream()
                .filter(rule -> UserSentinelResources.GET_USER_DETAIL.equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }
}
