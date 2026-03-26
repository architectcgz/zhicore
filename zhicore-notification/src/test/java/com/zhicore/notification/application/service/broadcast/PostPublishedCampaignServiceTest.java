package com.zhicore.notification.application.service.broadcast;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UserServiceClient;
import com.zhicore.api.dto.user.UserDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublishedCampaignService 测试")
class PostPublishedCampaignServiceTest {

    @Mock
    private NotificationCampaignRepository notificationCampaignRepository;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private UserServiceClient userServiceClient;

    private PostPublishedCampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new PostPublishedCampaignService(
                notificationCampaignRepository,
                idGeneratorFeignClient,
                userServiceClient,
                new BroadcastShardPlanner()
        );
    }

    @Test
    @DisplayName("应该基于关注数规划首个 shard 并写入 campaign")
    void shouldPlanCampaignUsingFollowersCount() {
        PostPublishedIntegrationEvent event = new PostPublishedIntegrationEvent(
                "evt-1",
                Instant.parse("2026-03-26T08:00:00Z"),
                9001L,
                7001L,
                "一篇新文章",
                "这里是一段摘要",
                Instant.parse("2026-03-26T08:00:01Z"),
                3L
        );
        UserDTO userDTO = new UserDTO();
        userDTO.setFollowersCount(42);

        when(notificationCampaignRepository.existsBySourceEventId("evt-1")).thenReturn(false);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(5001L));
        when(userServiceClient.getUserById(7001L)).thenReturn(ApiResponse.success(userDTO));
        when(notificationCampaignRepository.savePlanned(any(NotificationCampaign.class), any(NotificationCampaignShard.class)))
                .thenReturn(true);

        boolean planned = campaignService.planCampaign(event);

        ArgumentCaptor<NotificationCampaign> campaignCaptor = ArgumentCaptor.forClass(NotificationCampaign.class);
        ArgumentCaptor<NotificationCampaignShard> shardCaptor = ArgumentCaptor.forClass(NotificationCampaignShard.class);
        verify(notificationCampaignRepository).savePlanned(campaignCaptor.capture(), shardCaptor.capture());

        NotificationCampaign campaign = campaignCaptor.getValue();
        NotificationCampaignShard shard = shardCaptor.getValue();
        assertTrue(planned);
        assertEquals("POST_PUBLISHED", campaign.getCampaignType());
        assertEquals("evt-1", campaign.getSourceEventId());
        assertEquals(7001L, campaign.getAuthorId());
        assertEquals(9001L, campaign.getPostId());
        assertEquals(42, campaign.getAudienceEstimate());
        assertEquals("PLANNED", campaign.getStatus());
        assertEquals(0L, shard.getStartCursor());
        assertEquals(42, shard.getShardSize());
        assertEquals("PLANNED", shard.getStatus());
    }

    @Test
    @DisplayName("重复事件时应该保持幂等")
    void shouldBeIdempotentWhenCampaignAlreadyExists() {
        PostPublishedIntegrationEvent event = new PostPublishedIntegrationEvent(
                "evt-duplicate",
                Instant.parse("2026-03-26T08:00:00Z"),
                9002L,
                7002L,
                "重复事件文章",
                null,
                Instant.parse("2026-03-26T08:00:01Z"),
                4L
        );
        UserDTO userDTO = new UserDTO();
        userDTO.setFollowersCount(7);

        when(notificationCampaignRepository.existsBySourceEventId("evt-duplicate")).thenReturn(true);

        boolean planned = campaignService.planCampaign(event);

        assertFalse(planned);
        verify(userServiceClient, never()).getUserById(any());
        verify(notificationCampaignRepository, never()).savePlanned(any(NotificationCampaign.class), any());
    }

    @Test
    @DisplayName("旧版 schema 事件应该安全跳过而不是失败")
    void shouldSkipLegacySchemaEvent() {
        PostPublishedIntegrationEvent legacyEvent = new PostPublishedIntegrationEvent(
                "evt-legacy",
                Instant.parse("2026-03-26T08:00:00Z"),
                9003L,
                Instant.parse("2026-03-26T08:00:01Z"),
                4L
        );

        boolean planned = campaignService.planCampaign(legacyEvent);

        assertFalse(planned);
        verify(notificationCampaignRepository, never()).savePlanned(any(NotificationCampaign.class), any());
        verify(userServiceClient, never()).getUserById(any());
    }

    @Test
    @DisplayName("零受众时应该只保存 campaign 不创建空 shard")
    void shouldSkipShardCreationWhenAudienceIsZero() {
        PostPublishedIntegrationEvent event = new PostPublishedIntegrationEvent(
                "evt-zero",
                Instant.parse("2026-03-26T08:00:00Z"),
                9004L,
                7004L,
                "零受众文章",
                null,
                Instant.parse("2026-03-26T08:00:01Z"),
                4L
        );
        UserDTO userDTO = new UserDTO();
        userDTO.setFollowersCount(0);

        when(notificationCampaignRepository.existsBySourceEventId("evt-zero")).thenReturn(false);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(5004L));
        when(userServiceClient.getUserById(7004L)).thenReturn(ApiResponse.success(userDTO));
        when(notificationCampaignRepository.savePlanned(any(NotificationCampaign.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(true);

        boolean planned = campaignService.planCampaign(event);

        assertTrue(planned);
        verify(notificationCampaignRepository).savePlanned(any(NotificationCampaign.class), org.mockito.ArgumentMatchers.isNull());
    }
}
