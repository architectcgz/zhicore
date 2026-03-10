package com.zhicore.user.application.sentinel;

/**
 * 用户服务 Sentinel 方法级资源常量。
 */
public final class UserSentinelResources {

    private UserSentinelResources() {
    }

    public static final String GET_USER_DETAIL = "user:getUserDetail";
    public static final String GET_USER_SIMPLE = "user:getUserSimple";
    public static final String BATCH_GET_USERS_SIMPLE = "user:batchGetUsersSimple";
    public static final String GET_STRANGER_MESSAGE_SETTING = "user:getStrangerMessageSetting";
    public static final String GET_FOLLOWERS = "user:getFollowers";
    public static final String GET_FOLLOWINGS = "user:getFollowings";
    public static final String GET_FOLLOW_STATS = "user:getFollowStats";
    public static final String IS_FOLLOWING = "user:isFollowing";
    public static final String GET_CHECK_IN_STATS = "user:getCheckInStats";
    public static final String GET_MONTHLY_CHECK_IN_BITMAP = "user:getMonthlyCheckInBitmap";
    public static final String GET_BLOCKED_USERS = "user:getBlockedUsers";
    public static final String IS_BLOCKED = "user:isBlocked";
    public static final String QUERY_USERS = "user:queryUsers";
}
