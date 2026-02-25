package com.zhicore.notification.application.service;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.cache.NotificationRedisKeys;
import com.zhicore.notification.infrastructure.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    private UserServiceClient userServiceClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private NotificationAggregationService aggregationService;

    private static final Long USER_ID = 123L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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
            when(valueOperations.get(anyString())).thenReturn(cachedResult);

            // When
            PageResult<AggregatedNotificationVO> result = 
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            verify(notificationRepository, never()).findAggregatedNotifications(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Cache miss - empty result from database")
        void getAggregatedNotifications_EmptyResult() {
            // Given
            when(valueOperations.get(anyString())).thenReturn(null);
            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
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
            when(valueOperations.get(anyString())).thenReturn(null);

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

            // Mock user service
            UserSimpleDTO user1 = new UserSimpleDTO();
            user1.setId(456L);
            user1.setNickName("Zhang San");
            UserSimpleDTO user2 = new UserSimpleDTO();
            user2.setId(789L);
            user2.setNickName("Li Si");
            UserSimpleDTO user3 = new UserSimpleDTO();
            user3.setId(999L);
            user3.setNickName("Wang Wu");

            when(userServiceClient.getUsersSimple(anyList()))
                    .thenReturn(ApiResponse.success(Arrays.asList(user1, user2, user3)));

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
        @DisplayName("User service failure - graceful degradation")
        void getAggregatedNotifications_UserServiceFailed() {
            // Given
            when(valueOperations.get(anyString())).thenReturn(null);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.FOLLOW);
            dto.setTotalCount(1);
            dto.setUnreadCount(1);
            dto.setLatestTime(LocalDateTime.now());
            dto.setActorIds(List.of("456"));

            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);
            when(userServiceClient.getUsersSimple(anyList()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            // When
            PageResult<AggregatedNotificationVO> result = 
                    aggregationService.getAggregatedNotifications(USER_ID, 0, 20);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
            // User info fetch failed, recentActors should be empty
            assertTrue(result.getRecords().get(0).getRecentActors().isEmpty());
        }
    }

    @Nested
    @DisplayName("Cache Invalidation Tests")
    class InvalidateCacheTests {

        @Test
        @DisplayName("Invalidate cache - success")
        void invalidateCache_Success() {
            // Given
            Set<String> keys = Set.of(
                    NotificationRedisKeys.aggregatedList(String.valueOf(USER_ID), 0, 20),
                    NotificationRedisKeys.aggregatedList(String.valueOf(USER_ID), 1, 20)
            );
            when(redisTemplate.keys(anyString())).thenReturn(keys);

            // When
            aggregationService.invalidateCache(USER_ID);

            // Then
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("Invalidate cache - no keys found")
        void invalidateCache_NoKeys() {
            // Given
            when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

            // When
            aggregationService.invalidateCache(USER_ID);

            // Then
            verify(redisTemplate, never()).delete(anyCollection());
        }

        @Test
        @DisplayName("Invalidate cache - Redis exception handled gracefully")
        void invalidateCache_RedisException() {
            // Given
            when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("Redis error"));

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
            when(valueOperations.get(anyString())).thenReturn(null);

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

            UserSimpleDTO user = new UserSimpleDTO();
            user.setId(456L);
            user.setNickName("Zhang San");
            when(userServiceClient.getUsersSimple(anyList()))
                    .thenReturn(ApiResponse.success(List.of(user)));

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
            when(valueOperations.get(anyString())).thenReturn(null);

            AggregatedNotificationDTO dto = new AggregatedNotificationDTO();
            dto.setType(NotificationType.FOLLOW);
            dto.setTotalCount(10);
            dto.setUnreadCount(5);
            dto.setLatestTime(LocalDateTime.now());
            dto.setActorIds(Arrays.asList("456", "789", "999"));

            when(notificationRepository.findAggregatedNotifications(String.valueOf(USER_ID), 0, 20))
                    .thenReturn(List.of(dto));
            when(notificationRepository.countAggregatedGroups(String.valueOf(USER_ID))).thenReturn(1);

            UserSimpleDTO user = new UserSimpleDTO();
            user.setId(456L);
            user.setNickName("Zhang San");
            when(userServiceClient.getUsersSimple(anyList()))
                    .thenReturn(ApiResponse.success(List.of(user)));

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
