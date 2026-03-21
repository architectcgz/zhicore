package com.zhicore.idgenerator.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.idgenerator.service.sentinel.IdGeneratorRoutes;
import com.zhicore.idgenerator.service.sentinel.IdGeneratorSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IdGeneratorSentinelConfig 测试")
class IdGeneratorSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载 ID 生成服务热点规则")
    void shouldLoadIdGeneratorRules() {
        IdGeneratorSentinelProperties properties = new IdGeneratorSentinelProperties();
        IdGeneratorSentinelConfig config = new IdGeneratorSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> IdGeneratorRoutes.SNOWFLAKE.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> IdGeneratorSentinelResources.GENERATE_SNOWFLAKE_ID.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> IdGeneratorSentinelResources.GENERATE_BATCH_SNOWFLAKE_IDS.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> IdGeneratorSentinelResources.GENERATE_SEGMENT_ID.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        IdGeneratorSentinelProperties properties = new IdGeneratorSentinelProperties();
        IdGeneratorSentinelConfig config = new IdGeneratorSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long routeRules = FlowRuleManager.getRules().stream()
                .filter(rule -> IdGeneratorRoutes.SNOWFLAKE.equals(rule.getResource()))
                .count();
        long rules = FlowRuleManager.getRules().stream()
                .filter(rule -> IdGeneratorSentinelResources.GENERATE_SNOWFLAKE_ID.equals(rule.getResource()))
                .count();

        assertEquals(1L, routeRules);
        assertEquals(1L, rules);
    }

    @Test
    @DisplayName("相关配置变更后应该重新加载规则")
    void shouldReloadRulesWhenRelevantConfigurationChanges() {
        IdGeneratorSentinelProperties properties = new IdGeneratorSentinelProperties();
        IdGeneratorSentinelConfig config = new IdGeneratorSentinelConfig(properties);

        config.initFlowRules();
        properties.setSnowflakeQps(1234);

        config.onEnvironmentChange(new EnvironmentChangeEvent(Set.of("id-generator.sentinel.snowflake-qps")));

        assertEquals(1234.0, FlowRuleManager.getRules().stream()
                .filter(rule -> IdGeneratorSentinelResources.GENERATE_SNOWFLAKE_ID.equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }

    @Test
    @DisplayName("RefreshScope 刷新后应该重新加载规则")
    void shouldReloadRulesWhenRefreshScopeRefreshed() {
        IdGeneratorSentinelProperties properties = new IdGeneratorSentinelProperties();
        IdGeneratorSentinelConfig config = new IdGeneratorSentinelConfig(properties);

        config.initFlowRules();
        properties.setSnowflakeQps(4321);

        config.onRefreshScopeRefreshed();

        assertEquals(4321.0, FlowRuleManager.getRules().stream()
                .filter(rule -> IdGeneratorSentinelResources.GENERATE_SNOWFLAKE_ID.equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }
}
