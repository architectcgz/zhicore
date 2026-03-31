package com.zhicore.notification.application.service.campaign;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.dto.user.FollowerCursorPageDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.application.service.preference.NotificationPreferenceService;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.model.NotificationCampaignStatus;
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

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignShardRepository shardRepository;
    @SuppressWarnings("unused")
    private final NotificationDeliveryRepository deliveryRepository;
    @SuppressWarnings("unused")
    private final NotificationRepository notificationRepository;
    @SuppressWarnings("unused")
    private final NotificationCommandService notificationCommandService;
    @SuppressWarnings("unused")
    private final NotificationPreferenceService preferenceService;
    @SuppressWarnings("unused")
    private final NotificationPushService pushService;
    private final UserServiceClient userServiceClient;
    @SuppressWarnings("unused")
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    public void drain(NotificationCampaign campaign) {
        NotificationCampaign current = campaignRepository.findById(campaign.getId()).orElse(campaign);
        if (current.getStatusEnum() == NotificationCampaignStatus.COMPLETED) {
            return;
        }

        current.markProcessing();
        campaignRepository.update(current);

        try {
            Optional<NotificationCampaignShard> shard;
            while ((shard = shardRepository.claimNextPending(current.getId())).isPresent()) {
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
        try {
            FollowerCursorPageDTO page = loadFollowers(campaign, shard);
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
                campaign.getAuthorId(),
                shard.getAfterCreatedAt(),
                shard.getAfterFollowerId(),
                shard.getBatchSize()
        );
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "粉丝游标查询失败");
        }
        return response.getData();
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "通知ID生成失败");
        }
        return response.getData();
    }
}
