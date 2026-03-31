package com.zhicore.notification.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationCampaignShardPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationCampaignShardMapper extends BaseMapper<NotificationCampaignShardPO> {

    @Select("""
        WITH next_shard AS (
            SELECT id
            FROM notification_campaign_shard
            WHERE campaign_id = #{campaignId}
              AND status IN ('PENDING', 'FAILED', 'PLANNED')
            ORDER BY id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        )
        UPDATE notification_campaign_shard target
        SET status = 'RUNNING',
            error_message = NULL,
            updated_at = CURRENT_TIMESTAMP,
            completed_at = NULL
        FROM next_shard
        WHERE target.id = next_shard.id
        RETURNING target.*
        """)
    NotificationCampaignShardPO claimNextPending(@Param("campaignId") Long campaignId);

    @Select("""
        SELECT COUNT(*)
        FROM notification_campaign_shard
        WHERE campaign_id = #{campaignId}
          AND status IN ('PENDING', 'FAILED', 'PLANNED')
        """)
    int countPending(@Param("campaignId") Long campaignId);

    @Update("""
        UPDATE notification_campaign_shard
        SET status = #{status},
            error_message = #{errorMessage},
            updated_at = #{updatedAt},
            completed_at = #{completedAt}
        WHERE id = #{id}
        """)
    int updateState(NotificationCampaignShardPO po);
}
