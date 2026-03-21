package com.zhicore.admin.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.admin.application.sentinel.AdminSentinelResources;
import com.zhicore.common.sentinel.FlowRuleSupport;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理服务热点查询 Sentinel 规则。
 */
@Slf4j
@Configuration
public class AdminSentinelConfig {

    private static final String CONFIG_PREFIX = "admin.sentinel";

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

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (!hasRelevantChange(event)) {
            return;
        }
        log.info("检测到管理服务 Sentinel 配置变更，重新加载热点查询规则: {}", event.getKeys());
        initFlowRules();
    }

    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefreshScopeRefreshed() {
        log.info("检测到管理服务 RefreshScope 刷新事件，重新加载热点查询 Sentinel 规则");
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
