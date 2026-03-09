package com.zhicore.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sentinel FlowRule 装载工具。
 *
 * <p>按资源名合并规则，避免模块重复初始化时把相同资源的规则不断追加到全局 RuleManager。</p>
 */
public final class FlowRuleSupport {

    private FlowRuleSupport() {
    }

    public static void loadMergedRules(List<FlowRule> newRules) {
        Map<String, FlowRule> mergedRules = new LinkedHashMap<>();
        for (FlowRule existingRule : FlowRuleManager.getRules()) {
            mergedRules.put(existingRule.getResource(), existingRule);
        }
        for (FlowRule newRule : newRules) {
            mergedRules.put(newRule.getResource(), newRule);
        }
        FlowRuleManager.loadRules(new ArrayList<>(mergedRules.values()));
    }
}
