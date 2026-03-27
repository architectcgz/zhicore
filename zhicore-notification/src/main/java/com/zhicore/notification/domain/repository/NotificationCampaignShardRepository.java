package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationCampaignShard;

import java.util.Optional;

public interface NotificationCampaignShardRepository {

    void save(NotificationCampaignShard shard);

    Optional<NotificationCampaignShard> findNextPending(Long campaignId);

    int countPending(Long campaignId);

    void update(NotificationCampaignShard shard);
}
