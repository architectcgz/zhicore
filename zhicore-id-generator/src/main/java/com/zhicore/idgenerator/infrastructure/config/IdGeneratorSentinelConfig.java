package com.zhicore.idgenerator.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.idgenerator.infrastructure.sentinel.IdGeneratorRoutes;
import com.zhicore.idgenerator.infrastructure.sentinel.IdGeneratorSentinelResources;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * ID 生成服务 Sentinel 规则。
 */
@Slf4j
@Configuration
public class IdGeneratorSentinelConfig {

    private final IdGeneratorSentinelProperties properties;

    public IdGeneratorSentinelConfig(IdGeneratorSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("ID 生成服务 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildRule(IdGeneratorRoutes.SNOWFLAKE, properties.getSnowflakeQps()));
        rules.add(buildRule(IdGeneratorSentinelResources.GENERATE_SNOWFLAKE_ID, properties.getSnowflakeQps()));
        rules.add(buildRule(IdGeneratorSentinelResources.GENERATE_BATCH_SNOWFLAKE_IDS,
                properties.getBatchSnowflakeQps()));
        rules.add(buildRule(IdGeneratorSentinelResources.GENERATE_SEGMENT_ID, properties.getSegmentQps()));

        FlowRuleSupport.loadMergedRules(rules);
        log.info("ID 生成服务 Sentinel 规则已加载: snowflake={}qps, batchSnowflake={}qps, segment={}qps",
                properties.getSnowflakeQps(), properties.getBatchSnowflakeQps(), properties.getSegmentQps());
    }

    private FlowRule buildRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
