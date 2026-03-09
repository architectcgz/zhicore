package com.zhicore.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("FlowRuleSupport 测试")
class FlowRuleSupportTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    @DisplayName("相同资源再次加载时应该覆盖旧规则而不是重复追加")
    void shouldReplaceExistingRuleByResource() {
        FlowRuleManager.loadRules(List.of(new FlowRule("/api/v1/search/posts").setCount(100)));

        FlowRuleSupport.loadMergedRules(List.of(
                new FlowRule("/api/v1/search/posts").setCount(200),
                new FlowRule("/api/v1/search/hot").setCount(50)
        ));

        assertEquals(2, FlowRuleManager.getRules().size());
        assertEquals(200D, FlowRuleManager.getRules().stream()
                .filter(rule -> "/api/v1/search/posts".equals(rule.getResource()))
                .findFirst()
                .orElseThrow()
                .getCount());
    }
}
