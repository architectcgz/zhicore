package com.zhicore.idgenerator.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.idgenerator.infrastructure.sentinel.IdGeneratorRoutes;
import com.zhicore.idgenerator.service.sentinel.IdGeneratorSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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
}
