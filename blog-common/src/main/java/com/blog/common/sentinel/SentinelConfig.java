package com.blog.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 熔断降级配置
 * 
 * 配置各服务的熔断规则和流控规则
 *
 * @author Blog Team
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelConfig {

    private final SentinelProperties properties;

    public SentinelConfig(SentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initRules() {
        initDegradeRules();
        initFlowRules();
        log.info("Sentinel rules initialized");
    }

    /**
     * 初始化熔断规则
     */
    private void initDegradeRules() {
        List<DegradeRule> degradeRules = new ArrayList<>();

        // User Service 熔断规则 - 基于错误率
        DegradeRule userServiceRule = new DegradeRule("user-service")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(properties.getErrorRatioThreshold())
                .setTimeWindow(properties.getRecoveryTimeoutSeconds())
                .setMinRequestAmount(properties.getMinRequestAmount())
                .setStatIntervalMs(properties.getStatIntervalMs());
        degradeRules.add(userServiceRule);

        // Post Service 熔断规则 - 基于慢调用比例
        DegradeRule postServiceRule = new DegradeRule("post-service")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(properties.getSlowRatioThreshold())
                .setSlowRatioThreshold(properties.getSlowRequestMs())
                .setTimeWindow(properties.getRecoveryTimeoutSeconds())
                .setMinRequestAmount(properties.getMinRequestAmount())
                .setStatIntervalMs(properties.getStatIntervalMs());
        degradeRules.add(postServiceRule);

        // Comment Service 熔断规则 - 基于错误率
        DegradeRule commentServiceRule = new DegradeRule("comment-service")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(properties.getErrorRatioThreshold())
                .setTimeWindow(properties.getRecoveryTimeoutSeconds())
                .setMinRequestAmount(properties.getMinRequestAmount())
                .setStatIntervalMs(properties.getStatIntervalMs());
        degradeRules.add(commentServiceRule);

        // Message Service 熔断规则 - 基于错误率
        DegradeRule messageServiceRule = new DegradeRule("message-service")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(properties.getErrorRatioThreshold())
                .setTimeWindow(properties.getRecoveryTimeoutSeconds())
                .setMinRequestAmount(properties.getMinRequestAmount())
                .setStatIntervalMs(properties.getStatIntervalMs());
        degradeRules.add(messageServiceRule);

        // Notification Service 熔断规则 - 基于错误率
        DegradeRule notificationServiceRule = new DegradeRule("notification-service")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(properties.getErrorRatioThreshold())
                .setTimeWindow(properties.getRecoveryTimeoutSeconds())
                .setMinRequestAmount(properties.getMinRequestAmount())
                .setStatIntervalMs(properties.getStatIntervalMs());
        degradeRules.add(notificationServiceRule);

        // Search Service 熔断规则 - 基于慢调用比例
        DegradeRule searchServiceRule = new DegradeRule("search-service")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(properties.getSlowRatioThreshold())
                .setSlowRatioThreshold(properties.getSlowRequestMs())
                .setTimeWindow(properties.getRecoveryTimeoutSeconds())
                .setMinRequestAmount(properties.getMinRequestAmount())
                .setStatIntervalMs(properties.getStatIntervalMs());
        degradeRules.add(searchServiceRule);

        DegradeRuleManager.loadRules(degradeRules);
        log.info("Loaded {} degrade rules", degradeRules.size());
    }

    /**
     * 初始化流控规则
     */
    private void initFlowRules() {
        List<FlowRule> flowRules = new ArrayList<>();

        // 默认流控规则 - 每个服务 1000 QPS
        String[] services = {"user-service", "post-service", "comment-service", 
                            "message-service", "notification-service", "search-service"};
        
        for (String service : services) {
            FlowRule rule = new FlowRule(service)
                    .setGrade(RuleConstant.FLOW_GRADE_QPS)
                    .setCount(properties.getDefaultQpsLimit())
                    .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                    .setWarmUpPeriodSec(10);
            flowRules.add(rule);
        }

        FlowRuleManager.loadRules(flowRules);
        log.info("Loaded {} flow rules", flowRules.size());
    }
}
