package com.zhicore.ranking.infrastructure.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜 Sentinel 限流配置
 *
 * <p>注册 @SentinelResource 注解支持，并初始化排行榜接口的 QPS 限流规则。</p>
 */
@Slf4j
@Configuration
public class RankingSentinelConfig {

    /** 热榜查询资源名 */
    public static final String RESOURCE_HOT_POSTS = "ranking-hot-posts";
    /** 创作者排行资源名 */
    public static final String RESOURCE_CREATORS = "ranking-creators";
    /** 话题排行资源名 */
    public static final String RESOURCE_TOPICS = "ranking-topics";

    private final RankingSentinelProperties properties;

    public RankingSentinelConfig(RankingSentinelProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        rules.add(buildQpsRule(RESOURCE_HOT_POSTS, properties.getHotPostsQps()));
        rules.add(buildQpsRule(RESOURCE_CREATORS, properties.getCreatorQps()));
        rules.add(buildQpsRule(RESOURCE_TOPICS, properties.getTopicQps()));

        FlowRuleManager.loadRules(rules);
        log.info("排行榜 Sentinel 限流规则已加载: hotPosts={}qps, creators={}qps, topics={}qps",
                properties.getHotPostsQps(), properties.getCreatorQps(), properties.getTopicQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(10);
    }
}
