package com.zhicore.content.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.content.infrastructure.sentinel.ContentRoutes;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ContentSentinelConfig 测试")
class ContentSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载文章服务热点读接口规则")
    void shouldLoadContentRules() {
        ContentSentinelProperties properties = new ContentSentinelProperties();
        ContentSentinelConfig config = new ContentSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentRoutes.TAGS_HOT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_DETAIL.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_LIST.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_CONTENT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_TAG_DETAIL.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_LIKE_COUNT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_FAVORITE_COUNT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.ADMIN_QUERY_POSTS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.LIST_FAILED_OUTBOX.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        ContentSentinelProperties properties = new ContentSentinelProperties();
        ContentSentinelConfig config = new ContentSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long hotTagRouteRules = FlowRuleManager.getRules().stream()
                .filter(rule -> ContentRoutes.TAGS_HOT.equals(rule.getResource()))
                .count();
        long detailRules = FlowRuleManager.getRules().stream()
                .filter(rule -> ContentSentinelResources.GET_POST_DETAIL.equals(rule.getResource()))
                .count();
        long tagRules = FlowRuleManager.getRules().stream()
                .filter(rule -> ContentSentinelResources.GET_TAG_DETAIL.equals(rule.getResource()))
                .count();

        assertEquals(1L, hotTagRouteRules);
        assertEquals(1L, detailRules);
        assertEquals(1L, tagRules);
    }

    @Test
    @DisplayName("当外部数据源清空规则时应该补回缺失的本地默认规则")
    void shouldRestoreMissingRulesAfterExternalOverride() {
        ContentSentinelProperties properties = new ContentSentinelProperties();
        ContentSentinelConfig config = new ContentSentinelConfig(properties);

        config.initFlowRules();
        FlowRuleManager.loadRules(Collections.emptyList());

        config.reconcileMissingFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_DETAIL.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_LIST.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> ContentSentinelResources.GET_POST_CONTENT.equals(rule.getResource())));
    }
}
