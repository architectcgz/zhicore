package com.zhicore.ranking.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.ranking.application.sentinel.RankingSentinelResources;
import com.zhicore.ranking.infrastructure.sentinel.RankingRoutes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜 Sentinel 限流配置
 *
 * <p>基于 Sentinel Web Servlet Filter 自动注册的 URL 资源进行限流，
 * 限流触发时由 common 模块的统一 BlockExceptionHandler 返回 429。</p>
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
        String p = RankingRoutes.PREFIX;
        List<FlowRule> rankingRules = new ArrayList<>();

        // 文章排行榜：固定路径接口
        for (String url : List.of(
                p + "/posts/hot",
                p + "/posts/hot/candidates",
                p + "/posts/hot/details",
                p + "/posts/hot/scores",
                p + "/posts/daily",
                p + "/posts/daily/scores",
                p + "/posts/weekly",
                p + "/posts/weekly/scores",
                p + "/posts/monthly",
                p + "/posts/monthly/scores")) {
            rankingRules.add(buildQpsRule(url, properties.getHotPostsQps()));
        }
        // 文章排行榜：路径变量接口（UrlCleaner 归一化后的模式路径）
        for (String suffix : List.of("/rank", "/score")) {
            rankingRules.add(buildQpsRule(RankingRoutes.POSTS_ID + suffix, properties.getHotPostsQps()));
        }

        // 创作者排行榜：固定路径接口
        for (String url : List.of(
                p + "/creators/hot",
                p + "/creators/hot/scores")) {
            rankingRules.add(buildQpsRule(url, properties.getCreatorQps()));
        }
        // 创作者排行榜：路径变量接口
        for (String suffix : List.of("/rank", "/score")) {
            rankingRules.add(buildQpsRule(RankingRoutes.CREATORS_ID + suffix, properties.getCreatorQps()));
        }

        // 话题排行榜：固定路径接口
        for (String url : List.of(
                p + "/topics/hot",
                p + "/topics/hot/scores")) {
            rankingRules.add(buildQpsRule(url, properties.getTopicQps()));
        }
        // 话题排行榜：路径变量接口
        for (String suffix : List.of("/rank", "/score")) {
            rankingRules.add(buildQpsRule(RankingRoutes.TOPICS_ID + suffix, properties.getTopicQps()));
        }
        // 方法级热点资源：保护下游聚合逻辑，避免只在 Web 路由层限流
        rankingRules.add(buildQpsRule(RankingSentinelResources.HOT_POST_DETAILS, properties.getHotPostsQps()));
        rankingRules.add(buildQpsRule(RankingSentinelResources.RESOLVE_POST_METADATA, properties.getHotPostsQps()));

        FlowRuleSupport.loadMergedRules(rankingRules);

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
