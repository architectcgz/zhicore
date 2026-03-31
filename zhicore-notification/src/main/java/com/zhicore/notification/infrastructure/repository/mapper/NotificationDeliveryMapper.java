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
            dedupe_key, delivery_status, skip_reason, failure_reason, retry_count,
            last_attempt_at, next_retry_at, sent_at, created_at, updated_at
        ) VALUES (
            #{deliveryId}, #{recipientId}, #{campaignId}, #{notificationId}, #{channel}, #{notificationType},
            #{dedupeKey}, #{deliveryStatus}, #{skipReason}, #{failureReason}, #{retryCount},
            #{lastAttemptAt}, #{nextRetryAt}, #{sentAt}, #{createdAt}, #{updatedAt}
        )
        ON CONFLICT (dedupe_key) DO NOTHING
        """)
    int insertIgnore(NotificationDelivery delivery);

    @Select("""
        SELECT delivery_id,
               recipient_id,
               campaign_id,
               notification_id,
               channel,
               notification_type,
               dedupe_key,
               delivery_status,
               skip_reason,
               failure_reason,
               retry_count,
               last_attempt_at,
               next_retry_at,
               sent_at,
               created_at,
               updated_at
        FROM notification_delivery
        WHERE delivery_id = #{deliveryId}
        """)
    @Results(id = "notificationDeliveryResult", value = {
            @Result(property = "deliveryId", column = "delivery_id"),
            @Result(property = "recipientId", column = "recipient_id"),
            @Result(property = "campaignId", column = "campaign_id"),
            @Result(property = "notificationId", column = "notification_id"),
            @Result(property = "dedupeKey", column = "dedupe_key"),
            @Result(property = "deliveryStatus", column = "delivery_status"),
            @Result(property = "skipReason", column = "skip_reason"),
            @Result(property = "failureReason", column = "failure_reason"),
            @Result(property = "retryCount", column = "retry_count"),
            @Result(property = "lastAttemptAt", column = "last_attempt_at"),
            @Result(property = "nextRetryAt", column = "next_retry_at"),
            @Result(property = "sentAt", column = "sent_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    NotificationDelivery findById(@Param("deliveryId") Long deliveryId);

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

    @Update("""
        UPDATE notification_delivery
        SET notification_id = #{notificationId},
            delivery_status = #{deliveryStatus},
            skip_reason = #{skipReason},
            failure_reason = #{failureReason},
            retry_count = #{retryCount},
            last_attempt_at = #{lastAttemptAt},
            next_retry_at = #{nextRetryAt},
            sent_at = #{sentAt},
            updated_at = #{updatedAt}
        WHERE delivery_id = #{deliveryId}
        """)
    int updateState(NotificationDelivery delivery);

    @Select("""
        <script>
        SELECT delivery_id,
               recipient_id,
               campaign_id,
               notification_id,
               channel,
               notification_type,
               dedupe_key,
               delivery_status,
               skip_reason,
               failure_reason,
               retry_count,
               last_attempt_at,
               next_retry_at,
               sent_at,
               created_at,
               updated_at
        FROM notification_delivery
        WHERE 1 = 1
          <if test="campaignId != null">AND campaign_id = #{campaignId}</if>
          <if test="recipientId != null">AND recipient_id = #{recipientId}</if>
          <if test="channel != null and channel != ''">AND channel = #{channel}</if>
          <if test="status != null and status != ''">AND delivery_status = #{status}</if>
        ORDER BY created_at DESC, delivery_id DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    @Results(id = "notificationDeliveryQueryResult", value = {
            @Result(property = "deliveryId", column = "delivery_id"),
            @Result(property = "recipientId", column = "recipient_id"),
            @Result(property = "campaignId", column = "campaign_id"),
            @Result(property = "notificationId", column = "notification_id"),
            @Result(property = "dedupeKey", column = "dedupe_key"),
            @Result(property = "deliveryStatus", column = "delivery_status"),
            @Result(property = "skipReason", column = "skip_reason"),
            @Result(property = "failureReason", column = "failure_reason"),
            @Result(property = "retryCount", column = "retry_count"),
            @Result(property = "lastAttemptAt", column = "last_attempt_at"),
            @Result(property = "nextRetryAt", column = "next_retry_at"),
            @Result(property = "sentAt", column = "sent_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<NotificationDelivery> query(@Param("campaignId") Long campaignId,
                                     @Param("recipientId") Long recipientId,
                                     @Param("channel") String channel,
                                     @Param("status") String status,
                                     @Param("limit") int limit,
                                     @Param("offset") long offset);

    @Select("""
        <script>
        SELECT COUNT(*)
        FROM notification_delivery
        WHERE 1 = 1
          <if test="campaignId != null">AND campaign_id = #{campaignId}</if>
          <if test="recipientId != null">AND recipient_id = #{recipientId}</if>
          <if test="channel != null and channel != ''">AND channel = #{channel}</if>
          <if test="status != null and status != ''">AND delivery_status = #{status}</if>
        </script>
        """)
    long count(@Param("campaignId") Long campaignId,
               @Param("recipientId") Long recipientId,
               @Param("channel") String channel,
               @Param("status") String status);

    @Select("""
        SELECT delivery_id,
               recipient_id,
               campaign_id,
               notification_id,
               channel,
               notification_type,
               dedupe_key,
               delivery_status,
               skip_reason,
               failure_reason,
               retry_count,
               last_attempt_at,
               next_retry_at,
               sent_at,
               created_at,
               updated_at
        FROM notification_delivery
        WHERE recipient_id = #{recipientId}
          AND delivery_status = 'DIGEST_PENDING'
        ORDER BY created_at ASC, delivery_id ASC
        """)
    @Results(id = "notificationPendingDigestResult", value = {
            @Result(property = "deliveryId", column = "delivery_id"),
            @Result(property = "recipientId", column = "recipient_id"),
            @Result(property = "campaignId", column = "campaign_id"),
            @Result(property = "notificationId", column = "notification_id"),
            @Result(property = "dedupeKey", column = "dedupe_key"),
            @Result(property = "deliveryStatus", column = "delivery_status"),
            @Result(property = "skipReason", column = "skip_reason"),
            @Result(property = "failureReason", column = "failure_reason"),
            @Result(property = "retryCount", column = "retry_count"),
            @Result(property = "lastAttemptAt", column = "last_attempt_at"),
            @Result(property = "nextRetryAt", column = "next_retry_at"),
            @Result(property = "sentAt", column = "sent_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<NotificationDelivery> findPendingDigestDeliveries(@Param("recipientId") Long recipientId);
}
