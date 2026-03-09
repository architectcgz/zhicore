package com.zhicore.search.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.search.infrastructure.sentinel.SearchRoutes;
import com.zhicore.search.infrastructure.sentinel.SearchSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SearchSentinelConfig 测试")
class SearchSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载搜索服务 URL 级限流规则")
    void shouldLoadSearchUrlRules() {
        SearchSentinelProperties properties = new SearchSentinelProperties();
        SearchSentinelConfig config = new SearchSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchRoutes.POSTS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchRoutes.SUGGEST.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchRoutes.HOT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchRoutes.HISTORY.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchSentinelResources.SEARCH_POSTS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchSentinelResources.GET_SUGGESTIONS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchSentinelResources.GET_HOT_KEYWORDS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream().anyMatch(rule -> SearchSentinelResources.GET_USER_HISTORY.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是累加重复规则")
    void shouldReplaceRulesByResourceWhenInitializedTwice() {
        SearchSentinelProperties properties = new SearchSentinelProperties();
        SearchSentinelConfig config = new SearchSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long postsRules = FlowRuleManager.getRules().stream()
                .filter(rule -> SearchRoutes.POSTS.equals(rule.getResource()))
                .count();

        assertTrue(postsRules == 1);
    }

    @Test
    @DisplayName("禁用配置时不应该加载搜索限流规则")
    void shouldSkipWhenDisabled() {
        SearchSentinelProperties properties = new SearchSentinelProperties();
        properties.setEnabled(false);
        SearchSentinelConfig config = new SearchSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().isEmpty());
    }
}
