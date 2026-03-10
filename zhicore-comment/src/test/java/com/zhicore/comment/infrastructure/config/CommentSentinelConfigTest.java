package com.zhicore.comment.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.comment.application.sentinel.CommentSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CommentSentinelConfig 测试")
class CommentSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载评论服务热点读接口规则")
    void shouldLoadCommentRules() {
        CommentSentinelProperties properties = new CommentSentinelProperties();
        CommentSentinelConfig config = new CommentSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.GET_COMMENT_DETAIL.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_PAGE.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_CURSOR.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.GET_REPLIES_PAGE.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.GET_REPLIES_CURSOR.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.IS_COMMENT_LIKED.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.BATCH_CHECK_COMMENT_LIKED.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.GET_COMMENT_LIKE_COUNT.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> CommentSentinelResources.ADMIN_QUERY_COMMENTS.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        CommentSentinelProperties properties = new CommentSentinelProperties();
        CommentSentinelConfig config = new CommentSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long detailRules = FlowRuleManager.getRules().stream()
                .filter(rule -> CommentSentinelResources.GET_COMMENT_DETAIL.equals(rule.getResource()))
                .count();

        assertEquals(1L, detailRules);
    }
}
