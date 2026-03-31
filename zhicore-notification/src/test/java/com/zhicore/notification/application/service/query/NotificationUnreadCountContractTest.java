package com.zhicore.notification.application.service.query;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.notification.application.port.store.NotificationAggregationStore;
import com.zhicore.notification.application.port.store.NotificationUnreadCountStore;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.repository.NotificationGroupStateRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification unread count contract tests")
class NotificationUnreadCountContractTest {

    private static final Duration UNREAD_COUNT_TTL = Duration.ofMinutes(5);

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private NotificationUnreadCountStore notificationUnreadCountStore;

    @Mock
    private NotificationAggregationStore notificationAggregationStore;

    @Mock
    private NotificationGroupStateRepository notificationGroupStateRepository;

    private NotificationCommandService notificationCommandService;
    private NotificationQueryService notificationQueryService;

    @BeforeEach
    void setUp() {
        notificationCommandService = new NotificationCommandService(
                notificationRepository,
                idGeneratorFeignClient,
                notificationUnreadCountStore,
                notificationAggregationStore,
                notificationGroupStateRepository
        );
        notificationQueryService = new NotificationQueryService(notificationRepository, notificationUnreadCountStore);
    }

    @Test
    @DisplayName("create like notification should increment unread count without full cache evict")
    void createLikeNotification_shouldIncrementUnreadCountWithoutFullCacheEvict() {
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(101L));

        Notification notification = notificationCommandService.createLikeNotification(11L, 22L, "post", 33L);

        assertEquals(101L, notification.getId());
        verify(notificationRepository).save(any(Notification.class));
        verify(notificationGroupStateRepository).upsertOnNotificationCreated(any(Notification.class));
        verify(notificationUnreadCountStore).increment(11L, 1, UNREAD_COUNT_TTL);
        verify(notificationUnreadCountStore, never()).evict(11L);
        verify(notificationAggregationStore).evictUser(11L);
    }

    @Test
    @DisplayName("mark as read should decrement unread count without full cache evict")
    void markAsRead_shouldDecrementUnreadCountWithoutFullCacheEvict() {
        Notification notification = Notification.createCommentNotification(202L, 11L, 22L, 33L, 44L, "hello");
        when(notificationRepository.findById(202L)).thenReturn(Optional.of(notification));
        when(notificationRepository.markAsRead(202L, 11L)).thenReturn(1);

        notificationCommandService.markAsRead(202L, 11L);

        verify(notificationRepository).markAsRead(202L, 11L);
        verify(notificationGroupStateRepository).decrementUnreadCount(11L, "COMMENT:post:33");
        verify(notificationUnreadCountStore).decrement(11L, 1);
        verify(notificationUnreadCountStore, never()).evict(11L);
        verify(notificationAggregationStore).evictUser(11L);
    }

    @Test
    @DisplayName("query unread count should reload from database and repopulate cache")
    void getUnreadCount_shouldReloadFromDatabaseAndPopulateCache() {
        when(notificationUnreadCountStore.get(11L)).thenReturn(null);
        when(notificationRepository.countUnread(11L)).thenReturn(5);

        int unreadCount = notificationQueryService.getUnreadCount(11L);

        assertEquals(5, unreadCount);
        verify(notificationUnreadCountStore).set(11L, 5, UNREAD_COUNT_TTL);
        verify(notificationRepository).countUnread(11L);
        verify(notificationUnreadCountStore, never()).increment(anyLong(), anyLong(), any());
    }
}
