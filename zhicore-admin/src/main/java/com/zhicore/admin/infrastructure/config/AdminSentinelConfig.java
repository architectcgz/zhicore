package com.zhicore.admin.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.admin.application.sentinel.AdminSentinelResources;
import com.zhicore.common.sentinel.FlowRuleSupport;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理服务热点查询 Sentinel 规则。
 */
@Slf4j
@Configuration
public class AdminSentinelConfig {

    private final AdminSentinelProperties properties;

    public AdminSentinelConfig(AdminSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("管理服务热点查询 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildQpsRule(AdminSentinelResources.LIST_USERS, properties.getUserListQps()));
        rules.add(buildQpsRule(AdminSentinelResources.LIST_POSTS, properties.getPostListQps()));
        rules.add(buildQpsRule(AdminSentinelResources.LIST_COMMENTS, properties.getCommentListQps()));
        rules.add(buildQpsRule(AdminSentinelResources.LIST_PENDING_REPORTS, properties.getReportListQps()));
        rules.add(buildQpsRule(AdminSentinelResources.LIST_REPORTS_BY_STATUS, properties.getReportListQps()));

        FlowRuleSupport.loadMergedRules(rules);
        log.info("管理服务热点查询 Sentinel 规则已加载: users={}qps, posts={}qps, comments={}qps, reports={}qps",
                properties.getUserListQps(),
                properties.getPostListQps(),
                properties.getCommentListQps(),
                properties.getReportListQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
