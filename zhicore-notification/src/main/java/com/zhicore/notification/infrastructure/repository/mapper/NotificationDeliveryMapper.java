package com.zhicore.notification.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationDeliveryPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationDeliveryMapper extends BaseMapper<NotificationDeliveryPO> {

    @Insert("""
        INSERT INTO notification_delivery (
            id, campaign_id, shard_id, recipient_id, channel, dedupe_key, status,
            notification_id, skip_reason, created_at, updated_at, sent_at
        ) VALUES (
            #{id}, #{campaignId}, #{shardId}, #{recipientId}, #{channel}, #{dedupeKey}, #{status},
            #{notificationId}, #{skipReason}, #{createdAt}, #{updatedAt}, #{sentAt}
        )
        ON CONFLICT (dedupe_key) DO NOTHING
        """)
    int insertIgnore(NotificationDeliveryPO po);

    @Select("SELECT * FROM notification_delivery WHERE dedupe_key = #{dedupeKey}")
    NotificationDeliveryPO selectByDedupeKey(String dedupeKey);

    @Update("""
        UPDATE notification_delivery
        SET status = #{status},
            notification_id = #{notificationId},
            skip_reason = #{skipReason},
            updated_at = #{updatedAt},
            sent_at = #{sentAt}
        WHERE id = #{id}
        """)
    int updateState(NotificationDeliveryPO po);
}
