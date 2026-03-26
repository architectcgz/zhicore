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
import com.zhicore.notification.domain.model.NotificationGroupState;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationGroupStateRepository;
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

    @Mock
    private NotificationGroupStateRepository notificationGroupStateRepository;

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
            verify(notificationRepository, never()).findAggregatedNotifications(anyString(), anyInt(), anyInt());
            verify(notificationGroupStateRepository, never()).findPage(anyLong(), anyInt(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Cache miss - count matches and main list uses group state")
        void getAggregatedNotifications_GroupStateCountMatches_shouldUseGroupState() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);

            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationGroupStateRepository.countByRecipientId(USER_ID)).thenReturn(1);
            when(notificationGroupStateRepository.sumUnreadCount(USER_ID)).thenReturn(1);

            NotificationGroupState state = NotificationGroupState.builder()
                    .recipientId(USER_ID)
                    .groupKey("LIKE:post:200")
                    .notificationType(NotificationType.LIKE)
                    .latestNotificationId(2000L)
                    .totalCount(2)
                    .unreadCount(1)
                    .targetType("post")
                    .targetId("200")
                    .latestContent("group state row")
                    .latestTime(LocalDateTime.now())
                    .actorIds(List.of("456"))
                    .build();
            when(notificationGroupStateRepository.findPage(USER_ID, 0, 20, 3))
                    .thenReturn(List.of(state));

            UserSimpleDTO user1 = new UserSimpleDTO();
            user1.setId(456L);
            user1.setNickname("Zhang San");
            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenReturn(ApiResponse.success(Map.of(456L, user1)));

            // When
            PageResult<AggregatedNotificationVO> result =
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals(200L, result.getRecords().get(0).getTargetId());
            assertEquals("group state row", result.getRecords().get(0).getLatestContent());
            verify(notificationRepository, never()).findAggregatedNotifications(anyString(), anyInt(), anyInt());
            verify(notificationGroupStateRepository).countByRecipientId(USER_ID);
            verify(notificationGroupStateRepository).findPage(USER_ID, 0, 20, 3);
        }

        @Test
        @DisplayName("Cache miss - count mismatch falls back to database aggregation")
        void getAggregatedNotifications_GroupStateCountMismatch_shouldFallbackToDatabaseAggregation() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(2);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationGroupStateRepository.countByRecipientId(USER_ID)).thenReturn(1);
            when(notificationGroupStateRepository.sumUnreadCount(USER_ID)).thenReturn(1);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.LIKE);
            dto.setTargetType("post");
            dto.setTargetId("200");
            dto.setTotalCount(2);
            dto.setUnreadCount(1);
            dto.setLatestTime(LocalDateTime.now());
            dto.setLatestContent("database row");
            dto.setActorIds(List.of("456"));
            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));

            UserSimpleDTO user1 = new UserSimpleDTO();
            user1.setId(456L);
            user1.setNickname("Zhang San");
            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenReturn(ApiResponse.success(Map.of(456L, user1)));

            // When
            PageResult<AggregatedNotificationVO> result =
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            assertEquals(2, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals("database row", result.getRecords().get(0).getLatestContent());
            verify(notificationRepository).findAggregatedNotifications(String.valueOf(USER_ID), 0, 20);
            verify(notificationGroupStateRepository).countByRecipientId(USER_ID);
            verify(notificationGroupStateRepository, never()).findPage(anyLong(), anyInt(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Cache miss - incomplete projection page falls back to database aggregation")
        void getAggregatedNotifications_IncompleteProjectionPage_shouldFallbackToDatabaseAggregation() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 2)).thenReturn(null);
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(2);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(2);
            when(notificationGroupStateRepository.countByRecipientId(USER_ID)).thenReturn(2);
            when(notificationGroupStateRepository.sumUnreadCount(USER_ID)).thenReturn(2);

            NotificationGroupState state = NotificationGroupState.builder()
                    .recipientId(USER_ID)
                    .groupKey("LIKE:post:200")
                    .notificationType(NotificationType.LIKE)
                    .latestNotificationId(2000L)
                    .totalCount(2)
                    .unreadCount(1)
                    .targetType("post")
                    .targetId("200")
                    .latestContent("partial projection")
                    .latestTime(LocalDateTime.now())
                    .actorIds(List.of("456"))
                    .build();
            when(notificationGroupStateRepository.findPage(USER_ID, 0, 2, 3))
                    .thenReturn(List.of(state));

            AggregatedNotificationDTO dto1 = new AggregatedNotificationDTO();
            dto1.setType(NotificationType.LIKE);
            dto1.setTargetType("post");
            dto1.setTargetId("200");
            dto1.setTotalCount(2);
            dto1.setUnreadCount(1);
            dto1.setLatestTime(LocalDateTime.now());
            dto1.setLatestContent("database row 1");
            dto1.setActorIds(List.of("456"));

            AggregatedNotificationDTO dto2 = new AggregatedNotificationDTO();
            dto2.setType(NotificationType.FOLLOW);
            dto2.setTotalCount(1);
            dto2.setUnreadCount(1);
            dto2.setLatestTime(LocalDateTime.now().minusMinutes(1));
            dto2.setLatestContent("database row 2");
            dto2.setActorIds(List.of("789"));

            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 2))
                    .thenReturn(List.of(dto1, dto2));

            UserSimpleDTO user1 = new UserSimpleDTO();
            user1.setId(456L);
            user1.setNickname("Zhang San");
            UserSimpleDTO user2 = new UserSimpleDTO();
            user2.setId(789L);
            user2.setNickname("Li Si");
            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenReturn(ApiResponse.success(Map.of(456L, user1, 789L, user2)));

            // When
            PageResult<AggregatedNotificationVO> result =
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 2);

            // Then
            assertNotNull(result);
            assertEquals(2, result.getRecords().size());
            assertEquals("database row 1", result.getRecords().get(0).getLatestContent());
            verify(notificationGroupStateRepository).findPage(USER_ID, 0, 2, 3);
            verify(notificationRepository).findAggregatedNotifications(String.valueOf(USER_ID), 0, 2);
        }

        @Test
        @DisplayName("Cache miss - projection unread summary mismatch falls back to database aggregation")
        void getAggregatedNotifications_ProjectionUnreadMismatch_shouldFallbackToDatabaseAggregation() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(2);
            when(notificationGroupStateRepository.countByRecipientId(USER_ID)).thenReturn(1);
            when(notificationGroupStateRepository.sumUnreadCount(USER_ID)).thenReturn(1);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.LIKE);
            dto.setTargetType("post");
            dto.setTargetId("200");
            dto.setTotalCount(2);
            dto.setUnreadCount(2);
            dto.setLatestTime(LocalDateTime.now());
            dto.setLatestContent("database row");
            dto.setActorIds(List.of("456"));
            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));

            UserSimpleDTO user1 = new UserSimpleDTO();
            user1.setId(456L);
            user1.setNickname("Zhang San");
            when(userServiceClient.batchGetUsersSimple(anySet()))
                    .thenReturn(ApiResponse.success(Map.of(456L, user1)));

            // When
            PageResult<AggregatedNotificationVO> result =
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            assertEquals("database row", result.getRecords().get(0).getLatestContent());
            verify(notificationGroupStateRepository, never()).findPage(anyLong(), anyInt(), anyInt(), anyInt());
            verify(notificationRepository).findAggregatedNotifications(String.valueOf(USER_ID), 0, 20);
        }

        @Test
        @DisplayName("Cache miss - empty projection falls back to database")
        void getAggregatedNotifications_EmptyResult() {
            // Given
            when(notificationAggregationStore.get(USER_ID, 0, 20)).thenReturn(null);

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

            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(3);

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

            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(1);
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

            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(1);

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

            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);
            when(notificationRepository.countUnread(String.valueOf(USER_ID))).thenReturn(5);

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
