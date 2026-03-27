package com.zhicore.notification.application.service;

import com.zhicore.api.client.UserBatchSimpleClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.port.policy.NotificationAggregationPolicy;
import com.zhicore.notification.application.port.store.NotificationAggregationStore;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NotificationAggregationService unit tests
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationAggregationService Tests")
class NotificationAggregationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserBatchSimpleClient userServiceClient;

    @Mock
    private NotificationAggregationStore notificationAggregationStore;

    @Mock
    private NotificationAggregationPolicy aggregationPolicy;

    @InjectMocks
    private NotificationAggregationService aggregationService;

    private static final Long USER_ID = 123L;

    @BeforeEach
    void setUp() {
        lenient().when(aggregationPolicy.cacheTtl()).thenReturn(Duration.ofSeconds(300));
        lenient().when(aggregationPolicy.maxRecentActors()).thenReturn(3);
    }

    @Nested
    @DisplayName("Get Aggregated Notifications Tests")
    class GetAggregatedNotificationsTests {

        @Test
        @DisplayName("Cache hit - returns cached result")
        void getAggregatedNotifications_CacheHit() {
            // Given
            PageResult<AggregatedNotificationVO> cachedResult = PageResult.of(
                    0, 20, 0, Collections.emptyList());
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(cachedResult);

            // When
            PageResult<AggregatedNotificationVO> result = 
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            verify(notificationRepository, never()).findAggregatedNotifications(anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Cache miss - empty result from database")
        void getAggregatedNotifications_EmptyResult() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);
            when(notificationRepository.findAggregatedNotifications(USER_ID, 0, 20))
                    .thenReturn(Collections.emptyList());

            // When
            PageResult<AggregatedNotificationVO> result = 
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("Cache miss - returns data from database")
        void getAggregatedNotifications_WithData() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.LIKE);
            dto.setTargetType("post");
            dto.setTargetId("100");
            dto.setTotalCount(5);
            dto.setUnreadCount(3);
            dto.setLatestTime(LocalDateTime.now());
            dto.setLatestContent("liked your post");
            dto.setActorIds(Arrays.asList("456", "789", "999"));

            when(notificationRepository.findAggregatedNotifications(USER_ID, 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(USER_ID)).thenReturn(1);

            // Mock user service
            UserSimpleDTO user1 = new UserSimpleDTO();
            user1.setId(456L);
            user1.setNickname("Zhang San");
            UserSimpleDTO user2 = new UserSimpleDTO();
            user2.setId(789L);
            user2.setNickname("Li Si");
            UserSimpleDTO user3 = new UserSimpleDTO();
            user3.setId(999L);
            user3.setNickname("Wang Wu");

            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenReturn(ApiResponse.success(Map.of(
                            456L, user1,
                            789L, user2,
                            999L, user3
                    )));

            // When
            PageResult<AggregatedNotificationVO> result = 
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            
            AggregatedNotificationVO vo = result.getRecords().get(0);
            assertEquals(NotificationType.LIKE, vo.getType());
            assertEquals(5, vo.getTotalCount());
            assertEquals(3, vo.getUnreadCount());
            assertEquals(3, vo.getRecentActors().size());
            assertTrue(vo.getAggregatedContent().contains("Zhang San"));
            assertTrue(vo.getAggregatedContent().contains("5"));
        }

        @Test
        @DisplayName("User service failure - should fail fast without caching partial result")
        void getAggregatedNotifications_UserServiceFailed() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.FOLLOW);
            dto.setTotalCount(1);
            dto.setUnreadCount(1);
            dto.setLatestTime(LocalDateTime.now());
            dto.setActorIds(List.of("456"));

            when(notificationRepository.findAggregatedNotifications(USER_ID, 0, 20))
                    .thenReturn(List.of(dto));
            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            // When
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> aggregationService.getAggregatedNotifications(USER_ID, 0, 20));

            // Then
            assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
            verify(notificationAggregationStore, never()).set(anyLong(), anyInt(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("Cache Invalidation Tests")
    class InvalidateCacheTests {

        @Test
        @DisplayName("Invalidate cache - success")
        void invalidateCache_Success() {
            // When
            aggregationService.invalidateCache(USER_ID);

            // Then
            verify(notificationAggregationStore).evictUser(USER_ID);
        }

        @Test
        @DisplayName("Invalidate cache - no keys found")
        void invalidateCache_NoKeys() {
            // When
            aggregationService.invalidateCache(USER_ID);

            // Then
            verify(notificationAggregationStore).evictUser(USER_ID);
        }

        @Test
        @DisplayName("Invalidate cache - Redis exception handled gracefully")
        void invalidateCache_RedisException() {
            // Given
            doThrow(new RuntimeException("Redis error"))
                    .when(notificationAggregationStore).evictUser(USER_ID);

            // When & Then - should not throw exception
            assertDoesNotThrow(() -> aggregationService.invalidateCache(USER_ID));
        }
    }

    @Nested
    @DisplayName("Aggregated Content Generation Tests")
    class AggregatedContentTests {

        @Test
        @DisplayName("Single person like content")
        void aggregatedContent_SingleLike() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.LIKE);
            dto.setTargetType("post");
            dto.setTargetId("100");
            dto.setTotalCount(1);
            dto.setUnreadCount(1);
            dto.setLatestTime(LocalDateTime.now());
            dto.setActorIds(List.of("456"));

            when(notificationRepository.findAggregatedNotifications(USER_ID, 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(USER_ID)).thenReturn(1);

            UserSimpleDTO user = new UserSimpleDTO();
            user.setId(456L);
            user.setNickname("Zhang San");
            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenReturn(ApiResponse.success(Map.of(456L, user)));

            // When
            PageResult<AggregatedNotificationVO> result = 
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            String content = result.getRecords().get(0).getAggregatedContent();
            assertTrue(content.contains("Zhang San"));
        }

        @Test
        @DisplayName("Multiple people follow content")
        void aggregatedContent_MultipleFollow() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.FOLLOW);
            dto.setTotalCount(10);
            dto.setUnreadCount(5);
            dto.setLatestTime(LocalDateTime.now());
            dto.setActorIds(Arrays.asList("456", "789", "999"));

            when(notificationRepository.findAggregatedNotifications(USER_ID, 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(USER_ID)).thenReturn(1);

            UserSimpleDTO user = new UserSimpleDTO();
            user.setId(456L);
            user.setNickname("Zhang San");
            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenReturn(ApiResponse.success(Map.of(456L, user)));

            // When
            PageResult<AggregatedNotificationVO> result = 
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            String content = result.getRecords().get(0).getAggregatedContent();
            assertTrue(content.contains("Zhang San"));
            assertTrue(content.contains("10"));
        }
    }
}
