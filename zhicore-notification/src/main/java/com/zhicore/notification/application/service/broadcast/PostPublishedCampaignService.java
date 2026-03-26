package com.zhicore.notification.application.service.broadcast;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UserServiceClient;
import com.zhicore.api.dto.user.UserDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostPublishedCampaignService {

    private final NotificationCampaignRepository notificationCampaignRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final UserServiceClient userServiceClient;
    private final BroadcastShardPlanner broadcastShardPlanner;

    @Transactional
    public boolean planCampaign(PostPublishedIntegrationEvent event) {
        if (event.getSchemaVersion() != null && event.getSchemaVersion() < 2) {
            log.warn("跳过旧版发文广播事件: eventId={}, schemaVersion={}", event.getEventId(), event.getSchemaVersion());
            return false;
        }
        if (event.getAuthorId() == null) {
            log.warn("发文广播事件缺少作者信息，跳过规划: eventId={}, postId={}", event.getEventId(), event.getPostId());
            return false;
        }
        if (notificationCampaignRepository.existsBySourceEventId(event.getEventId())) {
            log.info("发文广播 campaign 已存在，跳过重复规划: eventId={}", event.getEventId());
            return false;
        }

        Long campaignId = generateId();
        int audienceEstimate = loadAudienceEstimate(event.getAuthorId());

        NotificationCampaign campaign = NotificationCampaign.planPostPublished(
                campaignId,
                event.getEventId(),
                event.getAuthorId(),
                event.getPostId(),
                audienceEstimate,
                event.getTitle(),
                event.getExcerpt(),
                event.getPublishedAt()
        );
        NotificationCampaignShard firstShard = audienceEstimate > 0
                ? broadcastShardPlanner.planFirstShard(campaignId, generateId(), audienceEstimate)
                : null;

        boolean planned = notificationCampaignRepository.savePlanned(campaign, firstShard);
        log.info("规划发文广播 campaign: eventId={}, campaignId={}, planned={}, audienceEstimate={}",
                event.getEventId(), campaignId, planned, audienceEstimate);
        return planned;
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "通知 campaign ID 生成失败");
        }
        return response.getData();
    }

    private int loadAudienceEstimate(Long authorId) {
        ApiResponse<UserDTO> response = userServiceClient.getUserById(authorId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "作者关注统计读取失败");
        }
        Integer followersCount = response.getData().getFollowersCount();
        return followersCount == null ? 0 : Math.max(followersCount, 0);
    }
}
