package com.zhicore.user.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.user.application.sentinel.UserSentinelResources;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户服务热点读接口 Sentinel 规则。
 */
@Slf4j
@Configuration
public class UserSentinelConfig {

    private final UserSentinelProperties properties;

    public UserSentinelConfig(UserSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("用户服务热点读接口 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildQpsRule(UserSentinelResources.GET_USER_DETAIL, properties.getUserDetailQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_USER_SIMPLE, properties.getUserSimpleQps()));
        rules.add(buildQpsRule(UserSentinelResources.BATCH_GET_USERS_SIMPLE, properties.getBatchUserSimpleQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_STRANGER_MESSAGE_SETTING, properties.getStrangerMessageSettingQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_FOLLOWERS, properties.getFollowersQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_FOLLOWINGS, properties.getFollowingsQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_FOLLOW_STATS, properties.getFollowStatsQps()));
        rules.add(buildQpsRule(UserSentinelResources.IS_FOLLOWING, properties.getIsFollowingQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_CHECK_IN_STATS, properties.getCheckInStatsQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_MONTHLY_CHECK_IN_BITMAP, properties.getMonthlyCheckInBitmapQps()));
        rules.add(buildQpsRule(UserSentinelResources.GET_BLOCKED_USERS, properties.getBlockedUsersQps()));
        rules.add(buildQpsRule(UserSentinelResources.IS_BLOCKED, properties.getIsBlockedQps()));
        rules.add(buildQpsRule(UserSentinelResources.QUERY_USERS, properties.getQueryUsersQps()));

        FlowRuleSupport.loadMergedRules(rules);
        log.info("用户服务热点读接口 Sentinel 规则已加载: detail={}qps, simple={}qps, batchSimple={}qps, strangerSetting={}qps, followers={}qps, followings={}qps, followStats={}qps, isFollowing={}qps, checkInStats={}qps, monthlyBitmap={}qps, blockedUsers={}qps, isBlocked={}qps, queryUsers={}qps",
                properties.getUserDetailQps(),
                properties.getUserSimpleQps(),
                properties.getBatchUserSimpleQps(),
                properties.getStrangerMessageSettingQps(),
                properties.getFollowersQps(),
                properties.getFollowingsQps(),
                properties.getFollowStatsQps(),
                properties.getIsFollowingQps(),
                properties.getCheckInStatsQps(),
                properties.getMonthlyCheckInBitmapQps(),
                properties.getBlockedUsersQps(),
                properties.getIsBlockedQps(),
                properties.getQueryUsersQps());
    }

    private FlowRule buildQpsRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
