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

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCampaignShardWorker {

    private static final String CHANNEL_INBOX = "INBOX";
    private static final String CHANNEL_PUSH = "PUSH";
    private static final String SKIP_PREFERENCE_DISABLED = "PREFERENCE_DISABLED";
    private static final String SKIP_CHANNEL_DISABLED_OR_DND = "CHANNEL_DISABLED_OR_DND";
    private static final String FAILURE_NOTIFICATION_MISSING = "NOTIFICATION_NOT_FOUND";
    private static final String FAILURE_PUSH_DELIVERY = "PUSH_DELIVERY_FAILED";

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
        NotificationDelivery inboxDelivery = getOrCreateDelivery(
                campaign, shard, recipientId, CHANNEL_INBOX);
        NotificationDelivery pushDelivery = getOrCreateDelivery(
                campaign, shard, recipientId, CHANNEL_PUSH);

        if (inboxDelivery.getStatus() == NotificationDeliveryStatus.SKIPPED) {
            if (pushDelivery.getStatus() == NotificationDeliveryStatus.PENDING) {
                pushDelivery.markSkipped(SKIP_PREFERENCE_DISABLED);
                deliveryRepository.update(pushDelivery);
            }
            return;
        }

        if (inboxDelivery.getStatus() != NotificationDeliveryStatus.SENT) {
            if (!preferenceService.isPreferenceEnabled(recipientId, NotificationType.POST_PUBLISHED)) {
                inboxDelivery.markSkipped(SKIP_PREFERENCE_DISABLED);
                deliveryRepository.update(inboxDelivery);
                if (pushDelivery.getStatus() == NotificationDeliveryStatus.PENDING) {
                    pushDelivery.markSkipped(SKIP_PREFERENCE_DISABLED);
                    deliveryRepository.update(pushDelivery);
                }
                return;
            }

            notificationCommandService.createPostPublishedNotificationIfAbsent(
                    inboxDelivery.getId(), recipientId, campaign.getAuthorId(), campaign.getPostId(), campaign.getId());
            inboxDelivery.markSent(inboxDelivery.getId());
            deliveryRepository.update(inboxDelivery);
        }

        if (pushDelivery.getStatus() == NotificationDeliveryStatus.SENT
                || pushDelivery.getStatus() == NotificationDeliveryStatus.SKIPPED
                || pushDelivery.getStatus() == NotificationDeliveryStatus.FAILED) {
            return;
        }

        Notification notification = notificationRepository.findById(inboxDelivery.getNotificationId())
                .orElse(null);
        if (notification == null) {
            pushDelivery.markFailed(FAILURE_NOTIFICATION_MISSING, null, inboxDelivery.getNotificationId());
            deliveryRepository.update(pushDelivery);
            return;
        }

        if (preferenceService.isDndActive(recipientId)) {
            pushDelivery.markSkipped(SKIP_CHANNEL_DISABLED_OR_DND, notification.getId(), null);
            deliveryRepository.update(pushDelivery);
            return;
        }

        if (pushService.push(String.valueOf(recipientId), notification)) {
            pushDelivery.markSent(notification.getId());
        } else {
            pushDelivery.markFailed(FAILURE_PUSH_DELIVERY, OffsetDateTime.now().plusMinutes(5), notification.getId());
        }
        deliveryRepository.update(pushDelivery);
    }

    private NotificationDelivery getOrCreateDelivery(NotificationCampaign campaign,
                                                     NotificationCampaignShard shard,
                                                     Long recipientId,
                                                     String channel) {
        String dedupeKey = campaign.getId() + ":" + channel + ":" + recipientId;
        return deliveryRepository.findByDedupeKey(dedupeKey)
                .orElseGet(() -> createPendingDelivery(campaign, shard, recipientId, channel, dedupeKey));
    }

    private NotificationDelivery createPendingDelivery(NotificationCampaign campaign,
                                                       NotificationCampaignShard shard,
                                                       Long recipientId,
                                                       String channel,
                                                       String dedupeKey) {
        NotificationDelivery pending = NotificationDelivery.pending(
                generateId(), campaign.getId(), shard.getId(), recipientId, channel, dedupeKey);
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
