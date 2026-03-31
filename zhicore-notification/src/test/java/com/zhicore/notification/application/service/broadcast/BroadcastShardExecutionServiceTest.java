package com.zhicore.notification.application.service.broadcast;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UserFollowerShardClient;
import com.zhicore.api.dto.user.FollowerShardItemDTO;
import com.zhicore.api.dto.user.FollowerShardPageDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.notification.application.service.channel.NotificationChannelPlanner;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BroadcastShardExecutionService 测试")
class BroadcastShardExecutionServiceTest {

    @Mock
    private NotificationCampaignRepository notificationCampaignRepository;

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private NotificationChannelPlanner notificationChannelPlanner;

    @Mock
    private UserFollowerShardClient userFollowerShardClient;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    private BroadcastShardExecutionService broadcastShardExecutionService;

    @BeforeEach
    void setUp() {
        broadcastShardExecutionService = new BroadcastShardExecutionService(
                notificationCampaignRepository,
                notificationDeliveryRepository,
                notificationChannelPlanner,
                userFollowerShardClient,
                notificationCommandService,
                idGeneratorFeignClient
        );
    }

    @Test
    @DisplayName("执行分片时应该将收件人拆分为 priority normal digest muted 四类")
    void executeShard_shouldSplitRecipientsIntoPriorityNormalDigestMuted() {
        NotificationCampaign campaign = NotificationCampaign.planPostPublished(
                7001L,
                "evt-1",
                22L,
                9001L,
                4,
                "一篇新作品",
                "摘要",
                Instant.parse("2026-03-26T08:00:00Z")
        );
        NotificationCampaignShard shard = NotificationCampaignShard.firstShard(8001L, 7001L, 4);

        FollowerShardItemDTO priorityItem = item(101L);
        FollowerShardItemDTO normalItem = item(102L);
        FollowerShardItemDTO digestItem = item(103L);
        FollowerShardItemDTO mutedItem = item(104L);
        FollowerShardPageDTO page = new FollowerShardPageDTO();
        page.setItems(List.of(priorityItem, normalItem, digestItem, mutedItem));
        page.setNextCursorFollowerId(104L);

        when(userFollowerShardClient.getFollowerShard(22L, 0L, 4)).thenReturn(ApiResponse.success(page));
        when(notificationChannelPlanner.planPostPublished(101L, 22L, LocalTime.NOON)).thenReturn(
                NotificationChannelPlanner.DeliveryPlan.priority(
                        java.util.EnumSet.of(
                                com.zhicore.notification.domain.model.NotificationChannel.IN_APP,
                                com.zhicore.notification.domain.model.NotificationChannel.WEBSOCKET
                        ),
                        "REALTIME_PUSH"
                )
        );
        when(notificationChannelPlanner.planPostPublished(102L, 22L, LocalTime.NOON)).thenReturn(
                NotificationChannelPlanner.DeliveryPlan.normal(
                        java.util.EnumSet.of(com.zhicore.notification.domain.model.NotificationChannel.IN_APP),
                        "INBOX_ONLY"
                )
        );
        when(notificationChannelPlanner.planPostPublished(103L, 22L, LocalTime.NOON)).thenReturn(
                NotificationChannelPlanner.DeliveryPlan.digest("AUTHOR_DIGEST_ONLY")
        );
        when(notificationChannelPlanner.planPostPublished(104L, 22L, LocalTime.NOON)).thenReturn(
                NotificationChannelPlanner.DeliveryPlan.muted("AUTHOR_MUTED")
        );
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(
                ApiResponse.success(5001L),
                ApiResponse.success(5002L),
                ApiResponse.success(5003L),
                ApiResponse.success(5004L),
                ApiResponse.success(5005L)
        );
        when(notificationDeliveryRepository.saveIfAbsent(any(NotificationDelivery.class))).thenReturn(true);
        when(notificationCommandService.createPostPublishedNotification(eq(101L), eq(22L), eq(9001L), any(), any()))
                .thenReturn(Notification.createPostPublishedNotification(90001L, 101L, 22L, 9001L, "g1", "c1"));
        when(notificationCommandService.createPostPublishedNotification(eq(102L), eq(22L), eq(9001L), any(), any()))
                .thenReturn(Notification.createPostPublishedNotification(90002L, 102L, 22L, 9001L, "g2", "c2"));

        BroadcastShardExecutionService.ShardExecutionSummary summary =
                broadcastShardExecutionService.executePostPublishedShard(campaign, shard, LocalTime.NOON);

        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, org.mockito.Mockito.times(5)).saveIfAbsent(deliveryCaptor.capture());
        verify(notificationDeliveryRepository).bindNotification(5001L, 90001L, "INBOX_CREATED");
        verify(notificationDeliveryRepository).bindNotification(5002L, 90001L, "WEBSOCKET_PENDING");
        verify(notificationDeliveryRepository).bindNotification(5003L, 90002L, "INBOX_CREATED");
        verify(notificationCampaignRepository).markShardExecuted(8001L, 104L, "COMPLETED");

        List<NotificationDelivery> deliveries = deliveryCaptor.getAllValues();
        assertEquals(5, deliveries.size());
        assertEquals("PLANNED", deliveries.get(0).getDeliveryStatus());
        assertEquals("PLANNED", deliveries.get(1).getDeliveryStatus());
        assertEquals("PLANNED", deliveries.get(2).getDeliveryStatus());
        assertEquals("DIGEST_PENDING", deliveries.get(3).getDeliveryStatus());
        assertEquals("SKIPPED", deliveries.get(4).getDeliveryStatus());
        assertEquals(90001L, deliveries.get(1).getNotificationId());
        assertEquals(4, summary.getProcessedCount());
        assertEquals(2, summary.getSuccessCount());
        assertEquals(2, summary.getSkippedCount());
        assertEquals(104L, summary.getEndCursor());
    }

    private FollowerShardItemDTO item(Long followerId) {
        FollowerShardItemDTO item = new FollowerShardItemDTO();
        item.setFollowerId(followerId);
        item.setCreatedAt(OffsetDateTime.of(2026, 3, 26, 12, 0, 0, 0, ZoneOffset.ofHours(8)));
        return item;
    }
}
