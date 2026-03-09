package com.zhicore.comment.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.comment.infrastructure.sentinel.CommentSentinelResources;
import com.zhicore.common.sentinel.FlowRuleSupport;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论服务热点读接口 Sentinel 规则。
 */
@Slf4j
@Configuration
public class CommentSentinelConfig {

    private final CommentSentinelProperties properties;

    public CommentSentinelConfig(CommentSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("评论服务热点读接口 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildQpsRule(CommentSentinelResources.GET_COMMENT_DETAIL, properties.getCommentDetailQps()));
        rules.add(buildQpsRule(CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_PAGE, properties.getTopLevelPageQps()));
        rules.add(buildQpsRule(CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_CURSOR, properties.getTopLevelCursorQps()));
        rules.add(buildQpsRule(CommentSentinelResources.GET_REPLIES_PAGE, properties.getRepliesPageQps()));
        rules.add(buildQpsRule(CommentSentinelResources.GET_REPLIES_CURSOR, properties.getRepliesCursorQps()));
        rules.add(buildQpsRule(CommentSentinelResources.IS_COMMENT_LIKED, properties.getCommentLikedQps()));
        rules.add(buildQpsRule(CommentSentinelResources.BATCH_CHECK_COMMENT_LIKED, properties.getBatchCommentLikedQps()));
        rules.add(buildQpsRule(CommentSentinelResources.GET_COMMENT_LIKE_COUNT, properties.getCommentLikeCountQps()));
        rules.add(buildQpsRule(CommentSentinelResources.ADMIN_QUERY_COMMENTS, properties.getAdminQueryCommentsQps()));

        FlowRuleSupport.loadMergedRules(rules);
        log.info("评论服务热点读接口 Sentinel 规则已加载: detail={}qps, topPage={}qps, topCursor={}qps, repliesPage={}qps, repliesCursor={}qps, liked={}qps, batchLiked={}qps, likeCount={}qps, adminQuery={}qps",
                properties.getCommentDetailQps(),
                properties.getTopLevelPageQps(),
                properties.getTopLevelCursorQps(),
                properties.getRepliesPageQps(),
                properties.getRepliesCursorQps(),
                properties.getCommentLikedQps(),
                properties.getBatchCommentLikedQps(),
                properties.getCommentLikeCountQps(),
                properties.getAdminQueryCommentsQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
