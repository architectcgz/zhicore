package com.zhicore.notification.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.notification.application.port.store.NotificationAggregationStore;
import com.zhicore.notification.application.port.store.NotificationUnreadCountStore;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification query/command service 测试")
class NotificationApplicationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private NotificationUnreadCountStore notificationUnreadCountStore;

    @Mock
    private NotificationAggregationStore notificationAggregationStore;

    private NotificationCommandService notificationCommandService;

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @BeforeEach
    void setUp() {
        notificationCommandService = new NotificationCommandService(
                notificationRepository,
                idGeneratorFeignClient,
                notificationUnreadCountStore,
                notificationAggregationStore,
                new TransactionCommitSignal()
        );
    }

    @Test
    @DisplayName("首次幂等创建成功时应该落库并失效缓存")
    void shouldCreateNotificationWhenAbsent() {
        when(notificationRepository.saveIfAbsent(any(Notification.class))).thenReturn(true);

        Optional<Notification> result = notificationCommandService.createFollowNotificationIfAbsent(101L, 11L, 22L);

        assertTrue(result.isPresent());
        assertEquals(101L, result.get().getId());
        verify(notificationRepository).saveIfAbsent(any(Notification.class));
        verify(notificationUnreadCountStore).increment(11L);
        verify(notificationAggregationStore).evictUser(11L);
    }

    @Test
    @DisplayName("事务提交前不应提前递增未读缓存或失效聚合缓存")
    void shouldDelayCacheMutationUntilAfterCommitWhenCreateSucceedsInsideTransaction() {
        when(notificationRepository.saveIfAbsent(any(Notification.class))).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        Optional<Notification> result = notificationCommandService.createFollowNotificationIfAbsent(101L, 11L, 22L);

        assertTrue(result.isPresent());
        verify(notificationUnreadCountStore, never()).increment(anyLong());
        verify(notificationAggregationStore, never()).evictUser(anyLong());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(notificationUnreadCountStore).increment(11L);
        verify(notificationAggregationStore).evictUser(11L);
    }

    @Test
    @DisplayName("重复幂等创建时不应重复失效缓存")
    void shouldSkipCacheInvalidationWhenDuplicate() {
        when(notificationRepository.saveIfAbsent(any(Notification.class))).thenReturn(false);

        Optional<Notification> result = notificationCommandService.createLikeNotificationIfAbsent(
                202L, 33L, 44L, "post", 55L);

        assertTrue(result.isEmpty());
        verify(notificationRepository).saveIfAbsent(any(Notification.class));
        verify(notificationUnreadCountStore, never()).increment(anyLong());
        verify(notificationAggregationStore, never()).evictUser(any());
    }

    @Test
    @DisplayName("通知ID生成失败时应该返回服务降级错误码")
    void shouldReturnServiceDegradedWhenGenerateIdFails() {
        when(idGeneratorFeignClient.generateSnowflakeId())
                .thenReturn(ApiResponse.fail(ResultCode.SERVICE_DEGRADED, "ID服务不可用"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> notificationCommandService.createFollowNotification(11L, 22L));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("通知ID生成失败", exception.getMessage());
    }

    @Test
    @DisplayName("标记他人通知已读时应该返回无权访问错误")
    void shouldRejectMarkAsReadWhenNotificationBelongsToAnotherUser() {
        Notification notification = Notification.createFollowNotification(101L, 22L, 33L);
        when(notificationRepository.findById(101L)).thenReturn(Optional.of(notification));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> notificationCommandService.markAsRead(101L, 11L));

        assertEquals(ResultCode.RESOURCE_ACCESS_DENIED.getCode(), exception.getCode());
        assertEquals("无权访问该通知", exception.getMessage());
        verify(notificationRepository, never()).markAsRead(any(), anyLong());
        verify(notificationUnreadCountStore, never()).evict(any());
        verify(notificationAggregationStore, never()).evictUser(any());
    }

    @Test
    @DisplayName("标记本人通知已读时应该更新仓储并失效缓存")
    void shouldMarkAsReadWhenNotificationBelongsToCurrentUser() {
        Notification notification = Notification.createFollowNotification(202L, 11L, 33L);
        when(notificationRepository.findById(202L)).thenReturn(Optional.of(notification));
        when(notificationRepository.markAsRead(202L, 11L)).thenReturn(true);

        notificationCommandService.markAsRead(202L, 11L);

        verify(notificationRepository).markAsRead(202L, 11L);
        verify(notificationUnreadCountStore).decrement(11L, 1);
        verify(notificationAggregationStore).evictUser(eq(11L));
    }

    @Test
    @DisplayName("并发场景下标记已读未命中行时不应重复扣减未读数")
    void shouldSkipUnreadDecrementWhenMarkAsReadAffectsNoRows() {
        Notification notification = Notification.createFollowNotification(303L, 11L, 44L);
        when(notificationRepository.findById(303L)).thenReturn(Optional.of(notification));
        when(notificationRepository.markAsRead(303L, 11L)).thenReturn(false);

        notificationCommandService.markAsRead(303L, 11L);

        verify(notificationRepository).markAsRead(303L, 11L);
        verify(notificationUnreadCountStore, never()).decrement(anyLong(), anyInt());
        verify(notificationAggregationStore, never()).evictUser(anyLong());
    }

    @Test
    @DisplayName("全部已读时应该按实际更新条数原子扣减未读数")
    void shouldDecrementUnreadCountByUpdatedRowsWhenMarkAllAsRead() {
        when(notificationRepository.markAllAsRead(11L)).thenReturn(3);

        notificationCommandService.markAllAsRead(11L);

        verify(notificationRepository).markAllAsRead(11L);
        verify(notificationUnreadCountStore).decrement(11L, 3);
        verify(notificationAggregationStore).evictUser(11L);
    }

    @Test
    @DisplayName("事务提交前不应提前扣减未读缓存或失效聚合缓存")
    void shouldDelayCacheMutationUntilAfterCommitWhenMarkAllAsReadInsideTransaction() {
        when(notificationRepository.markAllAsRead(11L)).thenReturn(3);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        notificationCommandService.markAllAsRead(11L);

        verify(notificationUnreadCountStore, never()).decrement(anyLong(), anyInt());
        verify(notificationAggregationStore, never()).evictUser(anyLong());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(notificationUnreadCountStore).decrement(11L, 3);
        verify(notificationAggregationStore).evictUser(11L);
    }
}
