package com.zhicore.notification.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.cache.NotificationRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationApplicationService 测试")
class NotificationApplicationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private NotificationApplicationService notificationApplicationService;

    @BeforeEach
    void setUp() {
        notificationApplicationService = new NotificationApplicationService(
                notificationRepository,
                idGeneratorFeignClient,
                redisTemplate
        );
    }

    @Test
    @DisplayName("首次幂等创建成功时应该落库并失效缓存")
    void shouldCreateNotificationWhenAbsent() {
        when(notificationRepository.saveIfAbsent(any(Notification.class))).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("k1", "k2"));

        Optional<Notification> result = notificationApplicationService.createFollowNotificationIfAbsent(101L, 11L, 22L);

        assertTrue(result.isPresent());
        assertEquals(101L, result.get().getId());
        verify(notificationRepository).saveIfAbsent(any(Notification.class));
        verify(redisTemplate).delete(NotificationRedisKeys.unreadCount("11"));
        verify(redisTemplate).delete(Set.of("k1", "k2"));
    }

    @Test
    @DisplayName("重复幂等创建时不应重复失效缓存")
    void shouldSkipCacheInvalidationWhenDuplicate() {
        when(notificationRepository.saveIfAbsent(any(Notification.class))).thenReturn(false);

        Optional<Notification> result = notificationApplicationService.createLikeNotificationIfAbsent(
                202L, 33L, 44L, "post", 55L);

        assertTrue(result.isEmpty());
        verify(notificationRepository).saveIfAbsent(any(Notification.class));
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).keys(anyString());
    }

    @Test
    @DisplayName("通知ID生成失败时应该返回服务降级错误码")
    void shouldReturnServiceDegradedWhenGenerateIdFails() {
        when(idGeneratorFeignClient.generateSnowflakeId())
                .thenReturn(ApiResponse.fail(ResultCode.SERVICE_DEGRADED, "ID服务不可用"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> notificationApplicationService.createFollowNotification(11L, 22L));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("通知ID生成失败", exception.getMessage());
    }

    @Test
    @DisplayName("标记他人通知已读时应该返回无权访问错误")
    void shouldRejectMarkAsReadWhenNotificationBelongsToAnotherUser() {
        Notification notification = Notification.createFollowNotification(101L, 22L, 33L);
        when(notificationRepository.findById(101L)).thenReturn(Optional.of(notification));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> notificationApplicationService.markAsRead(101L, 11L));

        assertEquals(ResultCode.RESOURCE_ACCESS_DENIED.getCode(), exception.getCode());
        assertEquals("无权访问该通知", exception.getMessage());
        verify(notificationRepository, never()).markAsRead(any(), anyString());
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).keys(anyString());
    }

    @Test
    @DisplayName("标记本人通知已读时应该更新仓储并失效缓存")
    void shouldMarkAsReadWhenNotificationBelongsToCurrentUser() {
        Notification notification = Notification.createFollowNotification(202L, 11L, 33L);
        when(notificationRepository.findById(202L)).thenReturn(Optional.of(notification));
        when(redisTemplate.keys(NotificationRedisKeys.aggregatedListPattern("11"))).thenReturn(Set.of("k1"));

        notificationApplicationService.markAsRead(202L, 11L);

        verify(notificationRepository).markAsRead(202L, "11");
        verify(redisTemplate).delete(NotificationRedisKeys.unreadCount("11"));
        verify(redisTemplate).delete(eq(Set.of("k1")));
    }
}
