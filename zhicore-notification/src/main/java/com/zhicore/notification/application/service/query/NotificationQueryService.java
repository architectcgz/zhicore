package com.zhicore.notification.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.notification.application.port.store.NotificationUnreadCountStore;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.application.sentinel.NotificationSentinelHandlers;
import com.zhicore.notification.application.sentinel.NotificationSentinelResources;
import com.zhicore.notification.domain.repository.NotificationRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 通知读服务。
 *
 * 负责未读数等轻量查询，不承载写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final Duration UNREAD_COUNT_TTL = Duration.ofMinutes(5);

    private final NotificationRepository notificationRepository;
    private final NotificationUnreadCountStore notificationUnreadCountStore;

    @SentinelResource(
            value = NotificationSentinelResources.GET_UNREAD_COUNT,
            blockHandlerClass = NotificationSentinelHandlers.class,
            blockHandler = "handleUnreadCountBlocked"
    )
    public int getUnreadCount(Long userId) {
        try {
            Integer cached = notificationUnreadCountStore.get(userId);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("获取未读计数缓存失败: {}", e.getMessage());
        }

        int count = notificationRepository.countUnread(String.valueOf(userId));

        repairUnreadCountCache(userId, count);
        return count;
    }

    public UnreadBreakdown getUnreadBreakdown(Long userId) {
        int totalCount = getUnreadCount(userId);
        Map<Integer, Integer> byCategory = notificationRepository.countUnreadByCategory(String.valueOf(userId));
        return new UnreadBreakdown(
                totalCount,
                byCategory.getOrDefault(NotificationCategory.SOCIAL.getCode(), 0),
                byCategory.getOrDefault(NotificationCategory.CONTENT.getCode(), 0),
                byCategory.getOrDefault(NotificationCategory.SYSTEM.getCode(), 0),
                byCategory.getOrDefault(NotificationCategory.SECURITY.getCode(), 0)
        );
    }

    private void repairUnreadCountCache(Long userId, int count) {
        try {
            notificationUnreadCountStore.set(userId, count, UNREAD_COUNT_TTL);
        } catch (Exception e) {
            log.warn("缓存未读计数失败: {}", e.getMessage());
        }
    }

    @Getter
    public static class UnreadBreakdown {
        private final int totalCount;
        private final int interactionCount;
        private final int contentCount;
        private final int systemCount;
        private final int securityCount;

        public UnreadBreakdown(int totalCount,
                               int interactionCount,
                               int contentCount,
                               int systemCount,
                               int securityCount) {
            this.totalCount = totalCount;
            this.interactionCount = interactionCount;
            this.contentCount = contentCount;
            this.systemCount = systemCount;
            this.securityCount = securityCount;
        }
    }
}
