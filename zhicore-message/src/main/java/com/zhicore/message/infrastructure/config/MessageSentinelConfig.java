package com.zhicore.message.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.message.application.sentinel.MessageSentinelResources;
import com.zhicore.message.infrastructure.sentinel.MessageRoutes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息服务热点读接口 Sentinel 规则。
 */
@Slf4j
@Configuration
public class MessageSentinelConfig {

    private static final String CONFIG_PREFIX = "message.sentinel";

    private final MessageSentinelProperties properties;

    public MessageSentinelConfig(MessageSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("消息服务热点读接口 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildQpsRule(MessageRoutes.UNREAD_COUNT, properties.getUnreadCountQps()));
        rules.add(buildQpsRule(MessageSentinelResources.GET_CONVERSATION_LIST, properties.getConversationListQps()));
        rules.add(buildQpsRule(MessageSentinelResources.GET_CONVERSATION, properties.getConversationDetailQps()));
        rules.add(buildQpsRule(MessageSentinelResources.GET_CONVERSATION_BY_USER, properties.getConversationDetailQps()));
        rules.add(buildQpsRule(MessageSentinelResources.GET_CONVERSATION_COUNT, properties.getConversationListQps()));
        rules.add(buildQpsRule(MessageSentinelResources.GET_MESSAGE_HISTORY, properties.getMessageHistoryQps()));
        rules.add(buildQpsRule(MessageSentinelResources.GET_UNREAD_COUNT, properties.getUnreadCountQps()));

        FlowRuleSupport.loadMergedRules(rules);
        log.info("消息服务热点读接口 Sentinel 规则已加载: list={}qps, detail={}qps, history={}qps, unread={}qps",
                properties.getConversationListQps(),
                properties.getConversationDetailQps(),
                properties.getMessageHistoryQps(),
                properties.getUnreadCountQps());
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (!hasRelevantChange(event)) {
            return;
        }
        log.info("检测到消息服务 Sentinel 配置变更，重新加载热点读接口规则: {}", event.getKeys());
        initFlowRules();
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefreshScopeRefreshed() {
        log.info("检测到消息服务 RefreshScope 刷新事件，重新加载热点读接口 Sentinel 规则");
        initFlowRules();
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }

    private boolean hasRelevantChange(EnvironmentChangeEvent event) {
        return event.getKeys().stream()
                .anyMatch(key -> key.equals(CONFIG_PREFIX) || key.startsWith(CONFIG_PREFIX + "."));
    }
}
