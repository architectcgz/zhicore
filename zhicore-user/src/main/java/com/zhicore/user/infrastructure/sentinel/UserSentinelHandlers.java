package com.zhicore.user.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.dto.FollowStatsVO;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.interfaces.dto.response.UserManageDTO;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户服务 Sentinel 方法级 block 处理器。
 */
public final class UserSentinelHandlers {

    private UserSentinelHandlers() {
    }

    public static UserVO handleGetUserDetailBlocked(Long userId, BlockException ex) {
        throw tooManyRequests("用户详情请求过于频繁，请稍后重试");
    }

    public static UserSimpleDTO handleGetUserSimpleBlocked(Long userId, BlockException ex) {
        throw tooManyRequests("用户简要信息请求过于频繁，请稍后重试");
    }

    public static Map<Long, UserSimpleDTO> handleBatchGetUsersSimpleBlocked(Set<Long> userIds, BlockException ex) {
        throw tooManyRequests("批量用户信息请求过于频繁，请稍后重试");
    }

    public static boolean handleGetStrangerMessageSettingBlocked(Long userId, BlockException ex) {
        throw tooManyRequests("陌生人消息设置请求过于频繁，请稍后重试");
    }

    public static List<UserVO> handleGetFollowersBlocked(Long userId, int page, int size, BlockException ex) {
        throw tooManyRequests("粉丝列表请求过于频繁，请稍后重试");
    }

    public static List<UserVO> handleGetFollowingsBlocked(Long userId, int page, int size, BlockException ex) {
        throw tooManyRequests("关注列表请求过于频繁，请稍后重试");
    }

    public static FollowStatsVO handleGetFollowStatsBlocked(Long userId, Long currentUserId, BlockException ex) {
        throw tooManyRequests("关注统计请求过于频繁，请稍后重试");
    }

    public static boolean handleIsFollowingBlocked(Long followerId, Long followingId, BlockException ex) {
        throw tooManyRequests("关注关系查询过于频繁，请稍后重试");
    }

    public static CheckInVO handleGetCheckInStatsBlocked(Long userId, BlockException ex) {
        throw tooManyRequests("签到统计请求过于频繁，请稍后重试");
    }

    public static long handleGetMonthlyCheckInBitmapBlocked(Long userId, YearMonth yearMonth, BlockException ex) {
        throw tooManyRequests("月度签到记录请求过于频繁，请稍后重试");
    }

    public static List<UserVO> handleGetBlockedUsersBlocked(Long blockerId, int page, int size, BlockException ex) {
        throw tooManyRequests("拉黑列表请求过于频繁，请稍后重试");
    }

    public static boolean handleIsBlockedBlocked(Long blockerId, Long blockedId, BlockException ex) {
        throw tooManyRequests("拉黑关系查询过于频繁，请稍后重试");
    }

    public static PageResult<UserManageDTO> handleQueryUsersBlocked(
            String keyword, String status, int page, int size, BlockException ex) {
        throw tooManyRequests("后台用户查询请求过于频繁，请稍后重试");
    }

    private static BusinessException tooManyRequests(String message) {
        return new BusinessException(ResultCode.TOO_MANY_REQUESTS, message);
    }
}
