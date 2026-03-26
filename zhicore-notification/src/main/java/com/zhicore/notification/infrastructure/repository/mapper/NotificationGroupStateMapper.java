package com.zhicore.notification.infrastructure.repository.mapper;

import com.zhicore.notification.domain.model.NotificationGroupState;
import com.zhicore.notification.infrastructure.repository.typehandler.StringArrayTypeHandler;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 通知聚合组状态 Mapper。
 */
@Mapper
public interface NotificationGroupStateMapper {

    @Insert("""
        INSERT INTO notification_group_state (
            recipient_id, group_key, notification_type, latest_notification_id, total_count, unread_count
        ) VALUES (
            #{state.recipientId}, #{state.groupKey}, #{state.notificationType}, #{state.latestNotificationId},
            #{state.totalCount}, #{state.unreadCount}
        )
        ON CONFLICT (recipient_id, group_key) DO UPDATE
        SET notification_type = EXCLUDED.notification_type,
            latest_notification_id = EXCLUDED.latest_notification_id,
            total_count = notification_group_state.total_count + EXCLUDED.total_count,
            unread_count = notification_group_state.unread_count + EXCLUDED.unread_count,
            updated_at = CURRENT_TIMESTAMP
        """)
    int upsert(@Param("state") NotificationGroupState state);

    @Select("""
        SELECT
            state.recipient_id AS recipientId,
            state.group_key AS groupKey,
            state.notification_type AS notificationType,
            state.latest_notification_id AS latestNotificationId,
            state.total_count AS totalCount,
            state.unread_count AS unreadCount,
            latest.target_type AS targetType,
            CAST(latest.target_id AS VARCHAR) AS targetId,
            latest.content AS latestContent,
            latest.created_at AS latestTime,
            COALESCE(recent.actor_ids, ARRAY[]::VARCHAR[]) AS actorIds
        FROM notification_group_state state
        JOIN notifications latest ON latest.id = state.latest_notification_id
        LEFT JOIN LATERAL (
            SELECT ARRAY_AGG(recent.actor_id ORDER BY recent.latest_created_at DESC) AS actor_ids
            FROM (
                SELECT CAST(notification.actor_id AS VARCHAR) AS actor_id,
                       MAX(notification.created_at) AS latest_created_at
                FROM notifications notification
                WHERE notification.recipient_id = state.recipient_id
                  AND notification.group_key = state.group_key
                  AND notification.actor_id IS NOT NULL
                GROUP BY notification.actor_id
                ORDER BY latest_created_at DESC
                LIMIT #{recentActorLimit}
            ) recent
        ) recent ON TRUE
        WHERE state.recipient_id = #{recipientId}
        ORDER BY latest.created_at DESC
        LIMIT #{size} OFFSET #{offset}
        """)
    @Results({
            @Result(property = "actorIds", column = "actorIds", typeHandler = StringArrayTypeHandler.class)
    })
    List<NotificationGroupState> findPage(@Param("recipientId") Long recipientId,
                                          @Param("offset") int offset,
                                          @Param("size") int size,
                                          @Param("recentActorLimit") int recentActorLimit);

    @Select("""
        SELECT COUNT(*)
        FROM notification_group_state
        WHERE recipient_id = #{recipientId}
        """)
    int countByRecipientId(@Param("recipientId") Long recipientId);

    @Update("""
        UPDATE notification_group_state
        SET unread_count = GREATEST(unread_count - 1, 0),
            updated_at = CURRENT_TIMESTAMP
        WHERE recipient_id = #{recipientId}
          AND group_key = #{groupKey}
          AND unread_count > 0
        """)
    int decrementUnreadCount(@Param("recipientId") Long recipientId,
                             @Param("groupKey") String groupKey);

    @Update("""
        UPDATE notification_group_state
        SET unread_count = 0,
            updated_at = CURRENT_TIMESTAMP
        WHERE recipient_id = #{recipientId}
          AND unread_count > 0
        """)
    int markAllAsRead(@Param("recipientId") Long recipientId);
}
