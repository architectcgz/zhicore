package com.zhicore.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCampaignShard {

    private Long shardId;
    private Long campaignId;
    private Long startCursor;
    private Long endCursor;
    private int shardSize;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public static NotificationCampaignShard firstShard(Long shardId, Long campaignId, int shardSize) {
        Instant now = Instant.now();
        return NotificationCampaignShard.builder()
                .shardId(shardId)
                .campaignId(campaignId)
                .startCursor(0L)
                .endCursor(null)
                .shardSize(Math.max(shardSize, 0))
                .status("PLANNED")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
