package com.zhicore.notification.application.service.query;

import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationMapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Notification unread count 合同测试")
class NotificationUnreadCountContractTest {

    @Test
    @DisplayName("通知仓储未读数查询应使用 Long 类型接收者 ID")
    void shouldUseLongRecipientIdInNotificationRepositoryCountUnread() throws Exception {
        Method method = NotificationRepository.class.getMethod("countUnread", Long.class);

        assertEquals(Long.class, method.getParameterTypes()[0]);
    }

    @Test
    @DisplayName("通知 Mapper 未读数查询应使用 Long 类型接收者 ID")
    void shouldUseLongRecipientIdInNotificationMapperCountUnread() throws Exception {
        Method method = NotificationMapper.class.getMethod("countUnread", Long.class);

        assertEquals(Long.class, method.getParameterTypes()[0]);
    }

    @Test
    @DisplayName("通知仓储聚合查询应使用 Long 类型接收者 ID")
    void shouldUseLongRecipientIdInNotificationRepositoryAggregationMethods() throws Exception {
        Method findAggregatedNotifications = NotificationRepository.class.getMethod(
                "findAggregatedNotifications", Long.class, int.class, int.class);
        Method countAggregatedGroups = NotificationRepository.class.getMethod(
                "countAggregatedGroups", Long.class);
        Method findByGroup = NotificationRepository.class.getMethod(
                "findByGroup", Long.class, NotificationType.class, String.class, String.class, int.class);
        Method markAsRead = NotificationRepository.class.getMethod(
                "markAsRead", Long.class, Long.class);
        Method markAllAsRead = NotificationRepository.class.getMethod(
                "markAllAsRead", Long.class);

        assertEquals(Long.class, findAggregatedNotifications.getParameterTypes()[0]);
        assertEquals(Long.class, countAggregatedGroups.getParameterTypes()[0]);
        assertEquals(Long.class, findByGroup.getParameterTypes()[0]);
        assertEquals(Long.class, markAsRead.getParameterTypes()[1]);
        assertEquals(Long.class, markAllAsRead.getParameterTypes()[0]);
    }

    @Test
    @DisplayName("通知 Mapper 聚合查询应使用 Long 类型接收者 ID")
    void shouldUseLongRecipientIdInNotificationMapperAggregationMethods() throws Exception {
        Method findAggregatedNotifications = NotificationMapper.class.getMethod(
                "findAggregatedNotifications", Long.class, int.class, int.class);
        Method countAggregatedGroups = NotificationMapper.class.getMethod(
                "countAggregatedGroups", Long.class);
        Method findByGroup = NotificationMapper.class.getMethod(
                "findByGroup", Long.class, int.class, String.class, String.class, int.class);
        Method markAllAsRead = NotificationMapper.class.getMethod(
                "markAllAsRead", Long.class);
        Method markAsRead = NotificationMapper.class.getMethod(
                "markAsRead", Long.class, Long.class);

        assertEquals(Long.class, findAggregatedNotifications.getParameterTypes()[0]);
        assertEquals(Long.class, countAggregatedGroups.getParameterTypes()[0]);
        assertEquals(Long.class, findByGroup.getParameterTypes()[0]);
        assertEquals(Long.class, markAllAsRead.getParameterTypes()[0]);
        assertEquals(Long.class, markAsRead.getParameterTypes()[1]);
    }

    @Test
    @DisplayName("聚合通知查询应显式将 type 编码映射为 NotificationType 枚举")
    void shouldMapNotificationTypeCodeWithExplicitTypeHandler() throws Exception {
        Method method = NotificationMapper.class.getMethod(
                "findAggregatedNotifications", Long.class, int.class, int.class);
        Results results = method.getAnnotation(Results.class);

        assertNotNull(results);
        Result typeResult = Arrays.stream(results.value())
                .filter(result -> "type".equals(result.property()))
                .findFirst()
                .orElse(null);

        assertNotNull(typeResult);
        assertTrue(
                typeResult.typeHandler().getName().endsWith("NotificationTypeCodeTypeHandler"),
                "type 字段必须通过显式 TypeHandler 做编码到枚举的映射");
    }

    @Test
    @DisplayName("聚合通知查询应显式将 latestTime 的时区时间映射为 LocalDateTime")
    void shouldMapLatestTimeWithExplicitTypeHandler() throws Exception {
        Method method = NotificationMapper.class.getMethod(
                "findAggregatedNotifications", Long.class, int.class, int.class);
        Results results = method.getAnnotation(Results.class);

        assertNotNull(results);
        Result latestTimeResult = Arrays.stream(results.value())
                .filter(result -> "latestTime".equals(result.property()))
                .findFirst()
                .orElse(null);

        assertNotNull(latestTimeResult);
        assertTrue(
                latestTimeResult.typeHandler().getName().endsWith("OffsetDateTimeToLocalDateTimeTypeHandler"),
                "latestTime 字段必须通过显式 TypeHandler 做 TIMESTAMPTZ 到 LocalDateTime 的映射");
    }
}
