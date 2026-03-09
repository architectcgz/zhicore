package com.zhicore.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SentinelConfig 测试")
class SentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(java.util.Collections.emptyList());
        DegradeRuleManager.loadRules(java.util.Collections.emptyList());
    }

    @Test
    @DisplayName("默认不应加载 common 内置的伪服务规则")
    void shouldSkipDefaultRulesWhenNotExplicitlyEnabled() {
        SentinelProperties properties = new SentinelProperties();
        properties.setLoadDefaultServiceRules(false);

        new SentinelConfig(properties).initRules();

        assertEquals(0, FlowRuleManager.getRules().size());
        assertEquals(0, DegradeRuleManager.getRules().size());
    }

    @Test
    @DisplayName("显式开启后才加载 common 内置服务规则")
    void shouldLoadDefaultRulesWhenExplicitlyEnabled() {
        SentinelProperties properties = new SentinelProperties();
        properties.setLoadDefaultServiceRules(true);

        new SentinelConfig(properties).initRules();

        assertTrue(!DegradeRuleManager.getRules().isEmpty());
        assertTrue(!FlowRuleManager.getRules().isEmpty());
    }
}
