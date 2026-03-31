package com.zhicore.notification.infrastructure.repository.mapper;

import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationCampaignMapper {

    @Insert("""
        INSERT INTO notification_campaign (
            campaign_id, campaign_type, source_event_id, author_id, post_id, audience_estimate,
            status, title, excerpt, published_at, created_at, updated_at
        ) VALUES (
            #{campaign.campaignId}, #{campaign.campaignType}, #{campaign.sourceEventId}, #{campaign.authorId},
            #{campaign.postId}, #{campaign.audienceEstimate}, #{campaign.status}, #{campaign.title},
            #{campaign.excerpt}, #{campaign.publishedAt}, #{campaign.createdAt}, #{campaign.updatedAt}
        )
        ON CONFLICT (source_event_id) DO NOTHING
        """)
    int insertCampaign(@Param("campaign") NotificationCampaign campaign);

    @Insert("""
        INSERT INTO notification_campaign_shard (
            shard_id, campaign_id, start_cursor, end_cursor, shard_size, status, created_at, updated_at
        ) VALUES (
            #{shard.shardId}, #{shard.campaignId}, #{shard.startCursor}, #{shard.endCursor},
            #{shard.shardSize}, #{shard.status}, #{shard.createdAt}, #{shard.updatedAt}
        )
        """)
    int insertShard(@Param("shard") NotificationCampaignShard shard);

    @Update("""
        UPDATE notification_campaign_shard
        SET end_cursor = #{endCursor},
            status = #{status},
            updated_at = CURRENT_TIMESTAMP
        WHERE shard_id = #{shardId}
        """)
    int updateShardExecution(@Param("shardId") Long shardId,
                             @Param("endCursor") Long endCursor,
                             @Param("status") String status);

    @Select("""
        SELECT EXISTS (
            SELECT 1
            FROM notification_campaign
            WHERE source_event_id = #{sourceEventId}
        )
        """)
    boolean existsBySourceEventId(@Param("sourceEventId") String sourceEventId);

    @Select("""
        SELECT campaign_id,
               campaign_type,
               source_event_id,
               author_id,
               post_id,
               audience_estimate,
               status,
               title,
               excerpt,
               published_at,
               created_at,
               updated_at
        FROM notification_campaign
        WHERE campaign_id = #{campaignId}
        """)
    NotificationCampaign findById(@Param("campaignId") Long campaignId);

    @Update("""
        UPDATE notification_campaign
        SET status = #{campaign.status},
            updated_at = #{campaign.updatedAt}
        WHERE campaign_id = #{campaign.campaignId}
        """)
    int updateCampaign(@Param("campaign") NotificationCampaign campaign);
}
