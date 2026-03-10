package com.zhicore.content.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.infrastructure.sentinel.ContentRoutes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

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

        FlowRuleSupport.loadMergedRules(rules);
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
