package com.zhicore.search.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.search.infrastructure.sentinel.SearchRoutes;
import com.zhicore.search.infrastructure.sentinel.SearchSentinelResources;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索服务 Sentinel URL 级限流配置。
 *
 * <p>基于 Sentinel Web Servlet Filter 自动注册的 URL 资源进行限流，
 * 规则按模块增量合并，不覆盖其他模块已经注册的规则。</p>
 */
@Slf4j
@Configuration
public class SearchSentinelConfig {

    private final SearchSentinelProperties properties;

    public SearchSentinelConfig(SearchSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("搜索服务 Sentinel URL 级限流已禁用");
            return;
        }

        List<FlowRule> searchRules = new ArrayList<>();
        searchRules.add(buildQpsRule(SearchRoutes.POSTS, properties.getPostsQps()));
        searchRules.add(buildQpsRule(SearchRoutes.SUGGEST, properties.getSuggestQps()));
        searchRules.add(buildQpsRule(SearchRoutes.HOT, properties.getHotKeywordsQps()));
        searchRules.add(buildQpsRule(SearchRoutes.HISTORY, properties.getHistoryQps()));
        searchRules.add(buildQpsRule(SearchSentinelResources.SEARCH_POSTS, properties.getPostsQps()));
        searchRules.add(buildQpsRule(SearchSentinelResources.GET_SUGGESTIONS, properties.getSuggestQps()));
        searchRules.add(buildQpsRule(SearchSentinelResources.GET_HOT_KEYWORDS, properties.getHotKeywordsQps()));
        searchRules.add(buildQpsRule(SearchSentinelResources.GET_USER_HISTORY, properties.getHistoryQps()));

        FlowRuleSupport.loadMergedRules(searchRules);

        log.info("搜索服务 Sentinel 限流规则已加载: posts={}qps, suggest={}qps, hot={}qps, history={}qps",
                properties.getPostsQps(),
                properties.getSuggestQps(),
                properties.getHotKeywordsQps(),
                properties.getHistoryQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
