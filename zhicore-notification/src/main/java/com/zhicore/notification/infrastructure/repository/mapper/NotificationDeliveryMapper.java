package com.zhicore.notification.infrastructure.repository.mapper;

import com.zhicore.notification.domain.model.NotificationDelivery;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationDeliveryMapper {

    @Insert("""
        INSERT INTO notification_delivery (
            delivery_id, recipient_id, campaign_id, notification_id, channel, notification_type,
            dedupe_key, delivery_status, failure_reason, created_at, updated_at
        ) VALUES (
            #{deliveryId}, #{recipientId}, #{campaignId}, #{notificationId}, #{channel}, #{notificationType},
            #{dedupeKey}, #{deliveryStatus}, #{failureReason}, #{createdAt}, #{updatedAt}
        )
        ON CONFLICT (dedupe_key) DO NOTHING
        """)
    int insertIgnore(NotificationDelivery delivery);

    @Update("""
        UPDATE notification_delivery
        SET notification_id = #{notificationId},
            delivery_status = #{deliveryStatus},
            updated_at = CURRENT_TIMESTAMP
        WHERE delivery_id = #{deliveryId}
        """)
    int bindNotification(@Param("deliveryId") Long deliveryId,
                         @Param("notificationId") Long notificationId,
                         @Param("deliveryStatus") String deliveryStatus);

    @Select("""
        SELECT delivery_id,
               recipient_id,
               campaign_id,
               notification_id,
               channel,
               notification_type,
               dedupe_key,
               delivery_status,
               failure_reason,
               created_at,
               updated_at
        FROM notification_delivery
        WHERE recipient_id = #{recipientId}
          AND delivery_status = 'DIGEST_PENDING'
        ORDER BY created_at ASC, delivery_id ASC
        """)
    @Results({
            @Result(property = "deliveryId", column = "delivery_id"),
            @Result(property = "recipientId", column = "recipient_id"),
            @Result(property = "campaignId", column = "campaign_id"),
            @Result(property = "notificationId", column = "notification_id"),
            @Result(property = "dedupeKey", column = "dedupe_key"),
            @Result(property = "deliveryStatus", column = "delivery_status"),
            @Result(property = "failureReason", column = "failure_reason")
    })
    List<NotificationDelivery> findPendingDigestDeliveries(@Param("recipientId") Long recipientId);
}
