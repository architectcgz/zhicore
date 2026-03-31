package com.zhicore.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("NotificationCampaign 测试")
class NotificationCampaignTest {

    @Test
    @DisplayName("应该规划文章发布 campaign 并标记为 PLANNED")
    void shouldPlanPostPublishedCampaign() {
        Instant publishedAt = Instant.parse("2026-03-26T08:00:00Z");

        NotificationCampaign campaign = NotificationCampaign.planPostPublished(
                1001L,
                "evt-post-published-1",
                2002L,
                3003L,
                128,
                "新文章标题",
                "文章摘要",
                publishedAt
        );

        assertEquals(1001L, campaign.getCampaignId());
        assertEquals("POST_PUBLISHED", campaign.getCampaignType());
        assertEquals("evt-post-published-1", campaign.getSourceEventId());
        assertEquals(2002L, campaign.getAuthorId());
        assertEquals(3003L, campaign.getPostId());
        assertEquals(128, campaign.getAudienceEstimate());
        assertEquals("PLANNED", campaign.getStatus());
        assertEquals("新文章标题", campaign.getTitle());
        assertEquals("文章摘要", campaign.getExcerpt());
        assertEquals(publishedAt, campaign.getPublishedAt());
        assertNotNull(campaign.getCreatedAt());
        assertNotNull(campaign.getUpdatedAt());
    }
}
