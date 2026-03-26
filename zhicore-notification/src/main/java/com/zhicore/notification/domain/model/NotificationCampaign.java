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
public class NotificationCampaign {

    private Long campaignId;
    private String campaignType;
    private String sourceEventId;
    private Long authorId;
    private Long postId;
    private int audienceEstimate;
    private String status;
    private String title;
    private String excerpt;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static NotificationCampaign planPostPublished(Long campaignId,
                                                         String sourceEventId,
                                                         Long authorId,
                                                         Long postId,
                                                         int audienceEstimate,
                                                         String title,
                                                         String excerpt,
                                                         Instant publishedAt) {
        Instant now = Instant.now();
        return NotificationCampaign.builder()
                .campaignId(campaignId)
                .campaignType("POST_PUBLISHED")
                .sourceEventId(sourceEventId)
                .authorId(authorId)
                .postId(postId)
                .audienceEstimate(Math.max(audienceEstimate, 0))
                .status("PLANNED")
                .title(title)
                .excerpt(excerpt)
                .publishedAt(publishedAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
