package com.zhicore.ranking.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.ranking.application.sentinel.RankingSentinelResources;
import com.zhicore.ranking.infrastructure.sentinel.RankingRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;

import java.util.Collections;
import java.util.Set;

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
                .anyMatch(rule -> (RankingRoutes.PREFIX + "/posts/hot/candidates").equals(rule.getResource())));
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

    @Test
    @DisplayName("相关配置变更后应该重新加载规则")
    void shouldReloadRulesWhenRelevantConfigurationChanges() {
        RankingSentinelProperties properties = new RankingSentinelProperties();
        RankingSentinelConfig config = new RankingSentinelConfig(properties);

        config.initFlowRules();
        properties.setHotPostsQps(1234);

        config.onEnvironmentChange(new EnvironmentChangeEvent(Set.of("ranking.sentinel.hot-posts-qps")));

        assertEquals(1234.0, FlowRuleManager.getRules().stream()
                .filter(rule -> (RankingRoutes.PREFIX + "/posts/hot").equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }

    @Test
    @DisplayName("RefreshScope 刷新后应该重新加载规则")
    void shouldReloadRulesWhenRefreshScopeRefreshed() {
        RankingSentinelProperties properties = new RankingSentinelProperties();
        RankingSentinelConfig config = new RankingSentinelConfig(properties);

        config.initFlowRules();
        properties.setHotPostsQps(4321);

        config.onRefreshScopeRefreshed();

        assertEquals(4321.0, FlowRuleManager.getRules().stream()
                .filter(rule -> (RankingRoutes.PREFIX + "/posts/hot").equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }
}
