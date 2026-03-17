package com.zhicore.content.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.infrastructure.sentinel.ContentRoutes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章服务热点读接口 Sentinel 规则。
 */
@Slf4j
@Configuration
public class ContentSentinelConfig {

    private final ContentSentinelProperties properties;

    public ContentSentinelConfig(ContentSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("文章服务热点读接口 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = buildLocalRules();
        FlowRuleSupport.loadMergedRules(rules);
        logLoadedRules();
    }

    /**
     * 在 Spring 应用完全就绪后再次兜底装载一次规则。
     *
     * <p>原因：Sentinel Nacos datasource 在本地 dataId 为空时，可能在启动后用空规则覆盖
     * `FlowRuleManager`，导致基于 @SentinelResource 的本地默认规则失效。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureFlowRulesAfterStartup() {
        reconcileMissingFlowRules();
    }

    /**
     * 周期性补回缺失的本地默认规则，避免空远端规则把默认限流清空。
     *
     * <p>仅补齐“缺失资源”，不会覆盖已经存在的同名规则，因此仍然允许显式远端规则生效。</p>
     */
    @Scheduled(
            fixedDelayString = "${content.sentinel.reconcile-interval-ms:30000}",
            initialDelayString = "${content.sentinel.reconcile-interval-ms:30000}"
    )
    public void reconcileMissingFlowRules() {
        if (!properties.isEnabled()) {
            return;
        }

        List<FlowRule> localRules = buildLocalRules();
        Set<String> existingResources = FlowRuleManager.getRules().stream()
                .map(FlowRule::getResource)
                .collect(Collectors.toSet());
        List<FlowRule> missingRules = localRules.stream()
                .filter(rule -> !existingResources.contains(rule.getResource()))
                .toList();

        if (missingRules.isEmpty()) {
            return;
        }

        FlowRuleSupport.loadMergedRules(missingRules);
        log.warn("检测到 Sentinel FlowRule 缺失，已补回 {} 条本地默认规则: {}",
                missingRules.size(),
                missingRules.stream().map(FlowRule::getResource).toList());
    }

    private List<FlowRule> buildLocalRules() {
        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildQpsRule(ContentRoutes.TAGS_HOT, properties.getHotTagsQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_POST_DETAIL, properties.getPostDetailQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_POST_LIST, properties.getPostListQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_POST_CONTENT, properties.getPostContentQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_TAG_DETAIL, properties.getTagDetailQps()));
        rules.add(buildQpsRule(ContentSentinelResources.LIST_TAGS, properties.getTagListQps()));
        rules.add(buildQpsRule(ContentSentinelResources.SEARCH_TAGS, properties.getTagSearchQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_POSTS_BY_TAG, properties.getTagPostsQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_HOT_TAGS, properties.getHotTagsQps()));
        rules.add(buildQpsRule(ContentSentinelResources.IS_POST_LIKED, properties.getPostLikedQps()));
        rules.add(buildQpsRule(ContentSentinelResources.BATCH_CHECK_POST_LIKED, properties.getBatchPostLikedQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_POST_LIKE_COUNT, properties.getPostLikeCountQps()));
        rules.add(buildQpsRule(ContentSentinelResources.IS_POST_FAVORITED, properties.getPostFavoritedQps()));
        rules.add(buildQpsRule(ContentSentinelResources.BATCH_CHECK_POST_FAVORITED,
                properties.getBatchPostFavoritedQps()));
        rules.add(buildQpsRule(ContentSentinelResources.GET_POST_FAVORITE_COUNT,
                properties.getPostFavoriteCountQps()));
        rules.add(buildQpsRule(ContentSentinelResources.ADMIN_QUERY_POSTS, properties.getAdminQueryPostsQps()));
        rules.add(buildQpsRule(ContentSentinelResources.LIST_FAILED_OUTBOX, properties.getOutboxFailedQps()));
        return rules;
    }

    private void logLoadedRules() {
        log.info("文章服务热点读接口 Sentinel 规则已加载: detail={}qps, list={}qps, content={}qps, tagDetail={}qps, " +
                        "tagList={}qps, tagSearch={}qps, tagPosts={}qps, hotTags={}qps, postLiked={}qps, " +
                        "batchPostLiked={}qps, postLikeCount={}qps, postFavorited={}qps, batchPostFavorited={}qps, " +
                        "postFavoriteCount={}qps, adminQueryPosts={}qps, failedOutbox={}qps",
                properties.getPostDetailQps(), properties.getPostListQps(), properties.getPostContentQps(),
                properties.getTagDetailQps(), properties.getTagListQps(), properties.getTagSearchQps(),
                properties.getTagPostsQps(), properties.getHotTagsQps(), properties.getPostLikedQps(),
                properties.getBatchPostLikedQps(), properties.getPostLikeCountQps(),
                properties.getPostFavoritedQps(), properties.getBatchPostFavoritedQps(),
                properties.getPostFavoriteCountQps(), properties.getAdminQueryPostsQps(),
                properties.getOutboxFailedQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
