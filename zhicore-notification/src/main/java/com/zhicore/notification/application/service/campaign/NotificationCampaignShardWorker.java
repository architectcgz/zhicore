package com.zhicore.notification.application.service.campaign;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.dto.user.FollowerCursorItemDTO;
import com.zhicore.api.dto.user.FollowerCursorPageDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.application.service.preference.NotificationPreferenceService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.model.NotificationDeliveryStatus;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import com.zhicore.notification.domain.repository.NotificationCampaignShardRepository;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.feign.UserServiceClient;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCampaignShardWorker {

    private static final String CHANNEL_INBOX = "INBOX";
    private static final String SKIP_PREFERENCE_DISABLED = "PREFERENCE_DISABLED";

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignShardRepository shardRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationCommandService notificationCommandService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationPushService pushService;
    private final UserServiceClient userServiceClient;
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    public void drain(NotificationCampaign campaign) {
        NotificationCampaign current = campaignRepository.findById(campaign.getId()).orElse(campaign);
        if (current.getStatus() == com.zhicore.notification.domain.model.NotificationCampaignStatus.COMPLETED) {
            return;
        }

        try {
            current.markProcessing();
            campaignRepository.update(current);
            Optional<NotificationCampaignShard> shard;
            while ((shard = shardRepository.findNextPending(current.getId())).isPresent()) {
                processShard(current, shard.get());
            }
            current.markCompleted();
            campaignRepository.update(current);
        } catch (Exception ex) {
            current.markFailed(ex.getMessage());
            campaignRepository.update(current);
            throw ex;
        }
    }

    @Transactional
    protected void processShard(NotificationCampaign campaign, NotificationCampaignShard shard) {
        shard.markRunning();
        shardRepository.update(shard);
        try {
            FollowerCursorPageDTO page = loadFollowers(campaign, shard);
            for (FollowerCursorItemDTO item : page.getItems()) {
                processRecipient(campaign, shard, item.getFollowerId());
            }

            if (page.isHasMore()) {
                NotificationCampaignShard nextShard = NotificationCampaignShard.create(
                        generateId(),
                        campaign.getId(),
                        page.getNextAfterCreatedAt(),
                        page.getNextAfterFollowerId(),
                        shard.getBatchSize()
                );
                shardRepository.save(nextShard);
            }

            shard.markCompleted();
            shardRepository.update(shard);
        } catch (Exception ex) {
            shard.markFailed(ex.getMessage());
            shardRepository.update(shard);
            throw ex;
        }
    }

    private FollowerCursorPageDTO loadFollowers(NotificationCampaign campaign, NotificationCampaignShard shard) {
        ApiResponse<FollowerCursorPageDTO> response = userServiceClient.getFollowersByCursor(
                campaign.getAuthorId(), shard.getAfterCreatedAt(), shard.getAfterFollowerId(), shard.getBatchSize());
        if (!response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "粉丝游标查询失败");
        }
        return response.getData();
    }

    private void processRecipient(NotificationCampaign campaign, NotificationCampaignShard shard, Long recipientId) {
        String dedupeKey = campaign.getId() + ":" + CHANNEL_INBOX + ":" + recipientId;
        NotificationDelivery delivery = deliveryRepository.findByDedupeKey(dedupeKey)
                .orElseGet(() -> createPendingDelivery(campaign, shard, recipientId, dedupeKey));

        if (delivery.getStatus() == NotificationDeliveryStatus.SENT
                || delivery.getStatus() == NotificationDeliveryStatus.SKIPPED) {
            return;
        }

        if (!preferenceService.isPreferenceEnabled(recipientId, NotificationType.POST_PUBLISHED)) {
            delivery.markSkipped(SKIP_PREFERENCE_DISABLED);
            deliveryRepository.update(delivery);
            return;
        }

        Optional<Notification> created = notificationCommandService.createPostPublishedNotificationIfAbsent(
                delivery.getId(), recipientId, campaign.getAuthorId(), campaign.getPostId(), campaign.getId());
        delivery.markSent(delivery.getId());
        deliveryRepository.update(delivery);

        Notification notification = created.orElseGet(() ->
                notificationRepository.findById(delivery.getId()).orElse(null)
        );

        if (notification != null && !preferenceService.isDndActive(recipientId)) {
            pushService.push(String.valueOf(recipientId), notification);
        }
    }

    private NotificationDelivery createPendingDelivery(NotificationCampaign campaign,
                                                       NotificationCampaignShard shard,
                                                       Long recipientId,
                                                       String dedupeKey) {
        NotificationDelivery pending = NotificationDelivery.pending(
                generateId(), campaign.getId(), shard.getId(), recipientId, CHANNEL_INBOX, dedupeKey);
        if (!deliveryRepository.saveIfAbsent(pending)) {
            return deliveryRepository.findByDedupeKey(dedupeKey)
                    .orElseThrow(() -> new BusinessException(ResultCode.OPERATION_FAILED, "delivery创建冲突"));
        }
        return pending;
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "通知ID生成失败");
        }
        return response.getData();
    }
}
