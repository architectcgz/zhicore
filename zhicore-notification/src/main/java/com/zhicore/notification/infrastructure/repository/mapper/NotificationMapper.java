package com.zhicore.notification.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.infrastructure.repository.po.NotificationPO;
import com.zhicore.notification.infrastructure.repository.po.UnreadCategoryCountPO;
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
 * 通知 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationPO> {

    @Insert("""
        INSERT INTO notifications (
            id, recipient_id, type, category, event_code, metadata, notification_type, actor_id,
            target_type, target_id, source_event_id, group_key, payload_json, content, importance,
            is_read, read_at, created_at
        ) VALUES (
            #{id}, #{recipientId}, #{type}, #{category}, #{eventCode}, #{metadata}, #{notificationType}, #{actorId},
            #{targetType}, #{targetId}, #{sourceEventId}, #{groupKey}, CAST(#{payloadJson} AS JSONB), #{content},
            #{importance}, #{isRead}, #{readAt}, #{createdAt}
        )
        """)
    int insertOne(NotificationPO notification);

    @Insert("""
        INSERT INTO notifications (
            id, recipient_id, type, category, event_code, metadata, notification_type, actor_id,
            target_type, target_id, source_event_id, group_key, payload_json, content, importance,
            is_read, read_at, created_at
        ) VALUES (
            #{id}, #{recipientId}, #{type}, #{category}, #{eventCode}, #{metadata}, #{notificationType}, #{actorId},
            #{targetType}, #{targetId}, #{sourceEventId}, #{groupKey}, CAST(#{payloadJson} AS JSONB), #{content},
            #{importance}, #{isRead}, #{readAt}, #{createdAt}
        )
        ON CONFLICT (id) DO NOTHING
        """)
    int insertIgnore(NotificationPO notification);

    @Select("""
        WITH grouped AS (
            SELECT
                type,
                target_type,
                target_id,
                COUNT(*) as total_count,
                COUNT(*) FILTER (WHERE is_read = false) as unread_count,
                ARRAY_AGG(DISTINCT actor_id ORDER BY actor_id)
                    FILTER (WHERE actor_id IS NOT NULL) as actor_ids
            FROM notifications
            WHERE recipient_id = #{recipientId}
            GROUP BY type, target_type, target_id
        ),
        latest AS (
            SELECT
                n.type,
                n.target_type,
                n.target_id,
                n.id as latest_notification_id,
                n.content as latest_content,
                n.created_at as latest_time,
                ROW_NUMBER() OVER (
                    PARTITION BY n.type, n.target_type, n.target_id
                    ORDER BY n.created_at DESC, n.id DESC
                ) as group_rn
            FROM notifications n
            WHERE n.recipient_id = #{recipientId}
        ),
        ranked AS (
            SELECT
                g.type,
                g.target_type,
                g.target_id,
                g.total_count,
                g.unread_count,
                l.latest_time,
                l.latest_notification_id,
                l.latest_content,
                g.actor_ids,
                ROW_NUMBER() OVER (ORDER BY l.latest_time DESC, l.latest_notification_id DESC) as rn
            FROM grouped g
            JOIN latest l ON (
                l.group_rn = 1
                AND l.type = g.type
                AND (l.target_type = g.target_type OR (l.target_type IS NULL AND g.target_type IS NULL))
                AND (l.target_id = g.target_id OR (l.target_id IS NULL AND g.target_id IS NULL))
            )
        )
        SELECT type, target_type as targetType, target_id as targetId,
               total_count as totalCount, unread_count as unreadCount,
               latest_time as latestTime, latest_notification_id as latestNotificationId,
               latest_content as latestContent, actor_ids as actorIds
        FROM ranked
        WHERE rn > #{offset} AND rn <= #{offset} + #{size}
        ORDER BY latest_time DESC, latest_notification_id DESC
        """)
    @Results({
        @Result(property = "actorIds", column = "actorIds", typeHandler = StringArrayTypeHandler.class)
    })
    List<AggregatedNotificationDTO> findAggregatedNotifications(@Param("recipientId") Long recipientId,
                                                                @Param("offset") int offset,
                                                                @Param("size") int size);

    @Select("""
        WITH grouped AS (
            SELECT
                type,
                target_type,
                target_id,
                COUNT(*) as total_count,
                COUNT(*) FILTER (WHERE is_read = false) as unread_count,
                ARRAY_AGG(DISTINCT actor_id ORDER BY actor_id)
                    FILTER (WHERE actor_id IS NOT NULL) as actor_ids
            FROM notifications
            WHERE recipient_id = #{recipientId}
              AND type = #{type}
              AND (target_type = #{targetType} OR (target_type IS NULL AND #{targetType} IS NULL))
              AND (target_id = #{targetId} OR (target_id IS NULL AND #{targetId} IS NULL))
            GROUP BY type, target_type, target_id
        )
        SELECT g.type,
               g.target_type as targetType,
               g.target_id as targetId,
               g.total_count as totalCount,
               g.unread_count as unreadCount,
               n.created_at as latestTime,
               n.id as latestNotificationId,
               n.content as latestContent,
               g.actor_ids as actorIds
        FROM grouped g
        JOIN LATERAL (
            SELECT id, content, created_at
            FROM notifications
            WHERE recipient_id = #{recipientId}
              AND type = g.type
              AND (target_type = g.target_type OR (target_type IS NULL AND g.target_type IS NULL))
              AND (target_id = g.target_id OR (target_id IS NULL AND g.target_id IS NULL))
            ORDER BY created_at DESC, id DESC
            LIMIT 1
        ) n ON TRUE
        """)
    @Results({
        @Result(property = "actorIds", column = "actorIds", typeHandler = StringArrayTypeHandler.class)
    })
    AggregatedNotificationDTO findAggregatedNotificationByGroup(@Param("recipientId") Long recipientId,
                                                                @Param("type") int type,
                                                                @Param("targetType") String targetType,
                                                                @Param("targetId") Long targetId);

    @Select("""
        SELECT COUNT(DISTINCT (type, target_type, target_id))
        FROM notifications
        WHERE recipient_id = #{recipientId}
        """)
    int countAggregatedGroups(@Param("recipientId") Long recipientId);

    @Select("""
        SELECT * FROM notifications
        WHERE recipient_id = #{recipientId}
          AND type = #{type}
          AND (target_type = #{targetType} OR (target_type IS NULL AND #{targetType} IS NULL))
          AND (target_id = #{targetId} OR (target_id IS NULL AND #{targetId} IS NULL))
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<NotificationPO> findByGroup(@Param("recipientId") Long recipientId,
                                     @Param("type") int type,
                                     @Param("targetType") String targetType,
                                     @Param("targetId") Long targetId,
                                     @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM notifications WHERE recipient_id = #{recipientId} AND is_read = false")
    int countUnread(@Param("recipientId") Long recipientId);

    @Select("""
        SELECT category, COUNT(*) AS unreadCount
        FROM notifications
        WHERE recipient_id = #{recipientId} AND is_read = false
        GROUP BY category
        """)
    List<UnreadCategoryCountPO> countUnreadByCategory(@Param("recipientId") Long recipientId);

    @Update("UPDATE notifications SET is_read = true, read_at = NOW() WHERE recipient_id = #{recipientId} AND is_read = false")
    int markAllAsRead(@Param("recipientId") Long recipientId);

    @Update("""
        UPDATE notifications
        SET is_read = true, read_at = NOW()
        WHERE id = #{id}
          AND recipient_id = #{recipientId}
          AND is_read = false
        """)
    int markAsRead(@Param("id") Long id, @Param("recipientId") Long recipientId);
}
