package com.zhicore.notification.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.notification.application.sentinel.NotificationSentinelResources;
import com.zhicore.notification.infrastructure.sentinel.NotificationRoutes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知服务 Sentinel 规则配置。
 */
@Slf4j
@Configuration
public class NotificationSentinelConfig {

    private final NotificationSentinelProperties properties;

    public NotificationSentinelConfig(NotificationSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("通知服务 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildQpsRule(NotificationRoutes.PREFIX, properties.getAggregatedQps()));
        rules.add(buildQpsRule(NotificationRoutes.UNREAD_COUNT, properties.getUnreadCountQps()));
        rules.add(buildQpsRule(NotificationSentinelResources.GET_AGGREGATED_NOTIFICATIONS, properties.getAggregatedQps()));
        rules.add(buildQpsRule(NotificationSentinelResources.GET_UNREAD_COUNT, properties.getUnreadCountQps()));

        FlowRuleSupport.loadMergedRules(rules);
        log.info("通知服务 Sentinel 规则已加载: aggregated={}qps, unreadCount={}qps",
                properties.getAggregatedQps(), properties.getUnreadCountQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
