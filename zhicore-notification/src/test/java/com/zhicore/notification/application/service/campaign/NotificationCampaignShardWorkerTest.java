package com.zhicore.notification.application.service.campaign;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.dto.user.FollowerCursorPageDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.application.service.preference.NotificationPreferenceService;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.model.NotificationCampaignShardStatus;
import com.zhicore.notification.domain.model.NotificationCampaignStatus;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import com.zhicore.notification.domain.repository.NotificationCampaignShardRepository;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.feign.UserServiceClient;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCampaignShardWorker 测试")
class NotificationCampaignShardWorkerTest {

    @Mock
    private NotificationCampaignRepository campaignRepository;

    @Mock
    private NotificationCampaignShardRepository shardRepository;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private NotificationPreferenceService preferenceService;

    @Mock
    private NotificationPushService pushService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @InjectMocks
    private NotificationCampaignShardWorker worker;

    @Test
    @DisplayName("drain 应通过原子 claim 领取 shard，处理空页时只落完成态更新")
    void shouldClaimShardAtomicallyAndOnlyPersistCompletedState() {
        OffsetDateTime now = OffsetDateTime.parse("2026-03-31T20:00:00+08:00");
        NotificationCampaign campaign = NotificationCampaign.reconstitute(
                101L, "evt-1", 202L, 303L, NotificationCampaignStatus.CREATED, now, now, null, null);
        NotificationCampaignShard shard = NotificationCampaignShard.reconstitute(
                401L, 101L, null, null, 50, NotificationCampaignShardStatus.RUNNING, now, now, null, null);

        when(campaignRepository.findById(101L)).thenReturn(Optional.of(campaign));
        when(shardRepository.claimNextPending(101L)).thenReturn(Optional.of(shard), Optional.empty());
        when(userServiceClient.getFollowersByCursor(eq(202L), any(), any(), eq(50)))
                .thenReturn(ApiResponse.success(FollowerCursorPageDTO.builder()
                        .items(List.of())
                        .nextAfterCreatedAt(null)
                        .nextAfterFollowerId(null)
                        .hasMore(false)
                        .build()));

        worker.drain(campaign);

        verify(shardRepository, times(2)).claimNextPending(101L);
        ArgumentCaptor<NotificationCampaignShard> shardCaptor = ArgumentCaptor.forClass(NotificationCampaignShard.class);
        verify(shardRepository, times(1)).update(shardCaptor.capture());
        assertEquals(NotificationCampaignShardStatus.COMPLETED, shardCaptor.getValue().getStatusEnum());
        verify(shardRepository, never()).update(argThat(arg -> arg.getStatusEnum() == NotificationCampaignShardStatus.RUNNING));
    }
}
