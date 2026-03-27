package com.zhicore.notification.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationCampaignPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationCampaignMapper extends BaseMapper<NotificationCampaignPO> {

    @Insert("""
        INSERT INTO notification_campaign (
            id, trigger_event_id, campaign_type, post_id, author_id, status, error_message,
            created_at, updated_at, completed_at
        ) VALUES (
            #{id}, #{triggerEventId}, #{campaignType}, #{postId}, #{authorId}, #{status}, #{errorMessage},
            #{createdAt}, #{updatedAt}, #{completedAt}
        )
        ON CONFLICT (trigger_event_id) DO NOTHING
        """)
    int insertIgnore(NotificationCampaignPO po);

    @Select("SELECT * FROM notification_campaign WHERE trigger_event_id = #{triggerEventId}")
    NotificationCampaignPO selectByTriggerEventId(String triggerEventId);

    @Update("""
        UPDATE notification_campaign
        SET status = #{status},
            error_message = #{errorMessage},
            updated_at = #{updatedAt},
            completed_at = #{completedAt}
        WHERE id = #{id}
        """)
    int updateState(NotificationCampaignPO po);
}
