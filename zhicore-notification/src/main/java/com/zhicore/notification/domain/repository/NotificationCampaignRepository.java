package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;

import java.util.Optional;

public interface NotificationCampaignRepository {

    boolean savePlanned(NotificationCampaign campaign, NotificationCampaignShard firstShard);

    void markShardExecuted(Long shardId, Long endCursor, String status);

    boolean existsBySourceEventId(String sourceEventId);

    Optional<NotificationCampaign> findById(Long campaignId);

    void update(NotificationCampaign campaign);
}
