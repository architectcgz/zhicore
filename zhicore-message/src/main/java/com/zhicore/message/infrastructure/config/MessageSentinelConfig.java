package com.zhicore.message.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.message.infrastructure.sentinel.MessageRoutes;
import com.zhicore.message.infrastructure.sentinel.MessageSentinelResources;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息服务热点读接口 Sentinel 规则。
 */
@Slf4j
@Configuration
public class MessageSentinelConfig {

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

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
