package com.zhicore.ranking.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜 Sentinel 限流配置
 *
 * <p>基于 Sentinel Web Servlet Filter 自动注册的 URL 资源进行限流，
 * 限流触发时由 {@link com.zhicore.ranking.infrastructure.sentinel.RankingBlockExceptionHandler} 统一返回 429。</p>
 *
 * <p>规则采用增量合并方式加载，不会覆盖其他模块已注册的限流规则。</p>
 */
@Slf4j
@Configuration
public class RankingSentinelConfig {

    private final RankingSentinelProperties properties;

    public RankingSentinelConfig(RankingSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rankingRules = new ArrayList<>();

        // Sentinel Web Filter 自动将请求 URL 注册为资源名
        // 文章排行榜相关接口
        for (String url : List.of(
                "/api/v1/ranking/posts/hot",
                "/api/v1/ranking/posts/hot/details",
                "/api/v1/ranking/posts/hot/scores",
                "/api/v1/ranking/posts/daily",
                "/api/v1/ranking/posts/daily/scores",
                "/api/v1/ranking/posts/weekly",
                "/api/v1/ranking/posts/weekly/scores",
                "/api/v1/ranking/posts/monthly",
                "/api/v1/ranking/posts/monthly/scores")) {
            rankingRules.add(buildQpsRule(url, properties.getHotPostsQps()));
        }

        // 创作者排行榜相关接口
        for (String url : List.of(
                "/api/v1/ranking/creators/hot",
                "/api/v1/ranking/creators/hot/scores")) {
            rankingRules.add(buildQpsRule(url, properties.getCreatorQps()));
        }

        // 话题排行榜相关接口
        for (String url : List.of(
                "/api/v1/ranking/topics/hot",
                "/api/v1/ranking/topics/hot/scores")) {
            rankingRules.add(buildQpsRule(url, properties.getTopicQps()));
        }

        // 增量合并：保留其他模块已注册的规则
        List<FlowRule> existingRules = new ArrayList<>(FlowRuleManager.getRules());
        existingRules.addAll(rankingRules);
        FlowRuleManager.loadRules(existingRules);

        log.info("排行榜 Sentinel 限流规则已加载: {} 条规则 (hotPosts={}qps, creators={}qps, topics={}qps)",
                rankingRules.size(),
                properties.getHotPostsQps(), properties.getCreatorQps(), properties.getTopicQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
