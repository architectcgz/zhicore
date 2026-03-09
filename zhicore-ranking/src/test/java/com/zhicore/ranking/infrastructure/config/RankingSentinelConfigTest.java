package com.zhicore.ranking.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.ranking.infrastructure.sentinel.RankingRoutes;
import com.zhicore.ranking.infrastructure.sentinel.RankingSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RankingSentinelConfig 测试")
class RankingSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载排行榜 URL 级限流规则")
    void shouldLoadRankingUrlRules() {
        RankingSentinelProperties properties = new RankingSentinelProperties();
        RankingSentinelConfig config = new RankingSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> (RankingRoutes.PREFIX + "/posts/hot").equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> (RankingRoutes.POSTS_ID + "/rank").equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> (RankingRoutes.CREATORS_ID + "/score").equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> (RankingRoutes.TOPICS_ID + "/score").equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> RankingSentinelResources.HOT_POST_DETAILS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> RankingSentinelResources.RESOLVE_POST_METADATA.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是累加重复规则")
    void shouldReplaceRulesByResourceWhenInitializedTwice() {
        RankingSentinelProperties properties = new RankingSentinelProperties();
        RankingSentinelConfig config = new RankingSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long postsHotRules = FlowRuleManager.getRules().stream()
                .filter(rule -> (RankingRoutes.PREFIX + "/posts/hot").equals(rule.getResource()))
                .count();

        assertEquals(1L, postsHotRules);
    }
}
