package com.zhicore.notification.application.service.campaign;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import com.zhicore.notification.domain.repository.NotificationCampaignShardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCampaignCommandService {

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignShardRepository shardRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    @Value("${notification.broadcast.batch-size:200}")
    private int batchSize;

    @Transactional
    public NotificationCampaign createOrLoad(PostPublishedIntegrationEvent event) {
        return campaignRepository.findByTriggerEventId(event.getEventId())
                .orElseGet(() -> createCampaign(event));
    }

    private NotificationCampaign createCampaign(PostPublishedIntegrationEvent event) {
        NotificationCampaign campaign = NotificationCampaign.create(
                generateId(), event.getEventId(), event.getPostId(), event.getAuthorId());
        if (!campaignRepository.saveIfAbsent(campaign)) {
            return campaignRepository.findByTriggerEventId(event.getEventId())
                    .orElseThrow(() -> new BusinessException(ResultCode.OPERATION_FAILED, "campaign创建冲突"));
        }

        NotificationCampaignShard initialShard = NotificationCampaignShard.create(
                generateId(), campaign.getId(), null, null, batchSize);
        shardRepository.save(initialShard);
        log.info("创建发布广播 campaign: campaignId={}, postId={}, authorId={}",
                campaign.getId(), campaign.getPostId(), campaign.getAuthorId());
        return campaign;
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "通知ID生成失败");
        }
        return response.getData();
    }
}
