package com.zhicore.notification.application.service.broadcast;

import com.zhicore.notification.domain.model.NotificationCampaignShard;
import org.springframework.stereotype.Component;

@Component
public class BroadcastShardPlanner {

    private static final int MAX_INITIAL_SHARD_SIZE = 2000;

    public NotificationCampaignShard planFirstShard(Long campaignId, Long shardId, int audienceEstimate) {
        if (audienceEstimate <= 0) {
            return null;
        }
        return NotificationCampaignShard.firstShard(
                shardId,
                campaignId,
                Math.min(audienceEstimate, MAX_INITIAL_SHARD_SIZE)
        );
    }
}
