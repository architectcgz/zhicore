package com.zhicore.notification.application.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.PageResult;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;

/**
 * 通知服务 Sentinel 方法级 block 处理器。
 */
public final class NotificationSentinelHandlers {

    private NotificationSentinelHandlers() {
    }

    public static PageResult<AggregatedNotificationVO> handleAggregatedNotificationsBlocked(
            Long userId, int page, int size, BlockException ex) {
        throw tooManyRequests("通知列表请求过于频繁，请稍后重试");
    }

    public static int handleUnreadCountBlocked(Long userId, BlockException ex) {
        throw tooManyRequests("未读计数请求过于频繁，请稍后重试");
    }

    private static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
