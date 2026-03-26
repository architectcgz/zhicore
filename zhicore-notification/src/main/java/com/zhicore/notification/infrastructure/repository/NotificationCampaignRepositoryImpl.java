package com.zhicore.notification.infrastructure.repository;

import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationCampaignMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationCampaignRepositoryImpl implements NotificationCampaignRepository {

    private final NotificationCampaignMapper notificationCampaignMapper;

    @Override
    @Transactional
    public boolean savePlanned(NotificationCampaign campaign, NotificationCampaignShard firstShard) {
        int inserted = notificationCampaignMapper.insertCampaign(campaign);
        if (inserted <= 0) {
            return false;
        }
        if (firstShard != null) {
            notificationCampaignMapper.insertShard(firstShard);
        }
        return true;
    }

    @Override
    public void markShardExecuted(Long shardId, Long endCursor, String status) {
        notificationCampaignMapper.updateShardExecution(shardId, endCursor, status);
    }

    @Override
    public boolean existsBySourceEventId(String sourceEventId) {
        return notificationCampaignMapper.existsBySourceEventId(sourceEventId);
    }
}
