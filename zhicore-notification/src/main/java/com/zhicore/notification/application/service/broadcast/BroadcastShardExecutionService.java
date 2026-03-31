package com.zhicore.notification.application.service.broadcast;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UserFollowerShardClient;
import com.zhicore.api.dto.user.FollowerShardItemDTO;
import com.zhicore.api.dto.user.FollowerShardPageDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.service.channel.NotificationChannelPlanner;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastShardExecutionService {

    private final NotificationCampaignRepository notificationCampaignRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationChannelPlanner notificationChannelPlanner;
    private final UserFollowerShardClient userFollowerShardClient;
    private final NotificationCommandService notificationCommandService;
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    @Transactional
    public ShardExecutionSummary executePostPublishedShard(NotificationCampaign campaign,
                                                           NotificationCampaignShard shard,
                                                           LocalTime currentTime) {
        FollowerShardPageDTO page = loadFollowerShard(campaign.getAuthorId(), shard.getStartCursor(), shard.getShardSize());
        int successCount = 0;
        int skippedCount = 0;
        Long endCursor = shard.getStartCursor();

        for (FollowerShardItemDTO item : page.getItems()) {
            Long recipientId = item.getFollowerId();
            endCursor = recipientId;
            NotificationChannelPlanner.DeliveryPlan deliveryPlan =
                    notificationChannelPlanner.planPostPublished(recipientId, campaign.getAuthorId(), currentTime);

            switch (deliveryPlan.getBucket()) {
                case PRIORITY, NORMAL -> {
                    Notification notification = createImmediateInboxDelivery(campaign, recipientId);
                    if (notification != null) {
                        successCount++;
                        if (deliveryPlan.usesChannel(NotificationChannel.WEBSOCKET)) {
                            createRealtimePushPlan(campaign, recipientId, notification.getId());
                        }
                    }
                }
                case DIGEST -> {
                    createDigestPlan(campaign, recipientId, deliveryPlan.getReason());
                    skippedCount++;
                }
                case MUTED -> {
                    createMutedPlan(campaign, recipientId, deliveryPlan.getReason());
                    skippedCount++;
                }
            }
        }

        notificationCampaignRepository.markShardExecuted(
                shard.getShardId(),
                page.getNextCursorFollowerId() != null ? page.getNextCursorFollowerId() : endCursor,
                "COMPLETED"
        );

        return ShardExecutionSummary.builder()
                .processedCount(page.getItems().size())
                .successCount(successCount)
                .skippedCount(skippedCount)
                .endCursor(page.getNextCursorFollowerId() != null ? page.getNextCursorFollowerId() : endCursor)
                .build();
    }

    private FollowerShardPageDTO loadFollowerShard(Long authorId, Long startCursor, int shardSize) {
        ApiResponse<FollowerShardPageDTO> response = userFollowerShardClient.getFollowerShard(authorId, startCursor, shardSize);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "粉丝分片读取失败");
        }
        return response.getData();
    }

    private Notification createImmediateInboxDelivery(NotificationCampaign campaign, Long recipientId) {
        NotificationDelivery delivery = NotificationDelivery.planned(
                generateId(),
                recipientId,
                campaign.getCampaignId(),
                NotificationChannel.IN_APP,
                NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                dedupeKey(campaign.getCampaignId(), recipientId, NotificationType.POST_PUBLISHED_BY_FOLLOWING, NotificationChannel.IN_APP)
        );
        if (!notificationDeliveryRepository.saveIfAbsent(delivery)) {
            log.info("站内投递计划已存在，跳过重复执行: campaignId={}, recipientId={}", campaign.getCampaignId(), recipientId);
            return null;
        }

        Notification notification = notificationCommandService.createPostPublishedNotification(
                recipientId,
                campaign.getAuthorId(),
                campaign.getPostId(),
                buildGroupKey(campaign),
                buildContent(campaign)
        );
        notificationDeliveryRepository.bindNotification(delivery.getDeliveryId(), notification.getId(), "INBOX_CREATED");
        return notification;
    }

    private void createRealtimePushPlan(NotificationCampaign campaign, Long recipientId, Long notificationId) {
        NotificationDelivery delivery = NotificationDelivery.planned(
                generateId(),
                recipientId,
                campaign.getCampaignId(),
                notificationId,
                NotificationChannel.WEBSOCKET,
                NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                dedupeKey(campaign.getCampaignId(), recipientId, NotificationType.POST_PUBLISHED_BY_FOLLOWING, NotificationChannel.WEBSOCKET)
        );
        if (notificationDeliveryRepository.saveIfAbsent(delivery)) {
            notificationDeliveryRepository.bindNotification(delivery.getDeliveryId(), notificationId, "WEBSOCKET_PENDING");
        }
    }

    private void createDigestPlan(NotificationCampaign campaign, Long recipientId, String reason) {
        NotificationDelivery delivery = NotificationDelivery.digestPending(
                generateId(),
                recipientId,
                campaign.getCampaignId(),
                dedupeKey(campaign.getCampaignId(), recipientId, NotificationType.POST_PUBLISHED_DIGEST, NotificationChannel.IN_APP),
                reason
        );
        notificationDeliveryRepository.saveIfAbsent(delivery);
    }

    private void createMutedPlan(NotificationCampaign campaign, Long recipientId, String reason) {
        NotificationDelivery delivery = NotificationDelivery.skipped(
                generateId(),
                recipientId,
                campaign.getCampaignId(),
                NotificationChannel.IN_APP,
                NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                dedupeKey(campaign.getCampaignId(), recipientId, NotificationType.POST_PUBLISHED_BY_FOLLOWING, NotificationChannel.IN_APP),
                reason
        );
        notificationDeliveryRepository.saveIfAbsent(delivery);
    }

    private String buildGroupKey(NotificationCampaign campaign) {
        return "author_publish_post:" + campaign.getPostId();
    }

    private String buildContent(NotificationCampaign campaign) {
        if (campaign.getTitle() != null && !campaign.getTitle().isBlank()) {
            return "你关注的作者发布了新作品：" + campaign.getTitle();
        }
        return "你关注的作者发布了新作品";
    }

    private String dedupeKey(Long campaignId,
                             Long recipientId,
                             NotificationType notificationType,
                             NotificationChannel channel) {
        return "campaign:" + campaignId
                + ":recipient:" + recipientId
                + ":type:" + notificationType.name()
                + ":channel:" + channel.name();
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "通知投递ID生成失败");
        }
        return response.getData();
    }

    @Getter
    @Builder
    public static class ShardExecutionSummary {
        private final int processedCount;
        private final int successCount;
        private final int skippedCount;
        private final Long endCursor;
    }
}
