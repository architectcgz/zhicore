package com.zhicore.notification.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.infrastructure.repository.po.NotificationPO;
import com.zhicore.notification.infrastructure.repository.po.UnreadCategoryCountPO;
import com.zhicore.notification.infrastructure.repository.typehandler.StringArrayTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 通知Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationPO> {

    @Insert("""
        INSERT INTO notifications (
            id, recipient_id, type, category, notification_type, actor_id, target_type, target_id,
            source_event_id, group_key, payload_json, content, importance, is_read, read_at, created_at
        ) VALUES (
            #{id}, #{recipientId}, #{type}, #{category}, #{notificationType}, #{actorId}, #{targetType}, #{targetId},
            #{sourceEventId}, #{groupKey}, CAST(#{payloadJson} AS JSONB), #{content}, #{importance}, #{isRead}, #{readAt}, #{createdAt}
        )
        """)
    int insertOne(NotificationPO notification);

    /**
     * 基于通知主键幂等插入。
     *
     * <p>重复事件会生成相同通知 ID，通过主键冲突直接忽略写入。</p>
     */
    @Insert("""
        INSERT INTO notifications (
            id, recipient_id, type, category, notification_type, actor_id, target_type, target_id,
            source_event_id, group_key, payload_json, content, importance, is_read, read_at, created_at
        ) VALUES (
            #{id}, #{recipientId}, #{type}, #{category}, #{notificationType}, #{actorId}, #{targetType}, #{targetId},
            #{sourceEventId}, #{groupKey}, CAST(#{payloadJson} AS JSONB), #{content}, #{importance}, #{isRead}, #{readAt}, #{createdAt}
        )
        ON CONFLICT (id) DO NOTHING
        """)
    int insertIgnore(NotificationPO notification);

    /**
     * 聚合查询通知
     * 使用窗口函数获取每组的统计信息和最新通知
     */
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
    List<AggregatedNotificationDTO> findAggregatedNotifications(
            @Param("recipientId") Long recipientId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /**
     * 获取聚合组总数
     */
    @Select("""
        SELECT COUNT(DISTINCT (type, target_type, target_id))
        FROM notifications
        WHERE recipient_id = #{recipientId}
        """)
    int countAggregatedGroups(@Param("recipientId") Long recipientId);

    /**
     * 查询某个聚合组的详细通知列表
     */
    @Select("""
        SELECT * FROM notifications
        WHERE recipient_id = #{recipientId}
          AND type = #{type}
          AND (target_type = #{targetType} OR (target_type IS NULL AND #{targetType} IS NULL))
          AND (target_id = #{targetId} OR (target_id IS NULL AND #{targetId} IS NULL))
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<NotificationPO> findByGroup(
            @Param("recipientId") Long recipientId,
            @Param("type") int type,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("limit") int limit
    );

    /**
     * 统计未读通知数量
     */
    @Select("SELECT COUNT(*) FROM notifications WHERE recipient_id = #{recipientId} AND is_read = false")
    int countUnread(@Param("recipientId") Long recipientId);

    @Select("""
        SELECT category, COUNT(*) AS unreadCount
        FROM notifications
        WHERE recipient_id = #{recipientId} AND is_read = false
        GROUP BY category
        """)
    List<UnreadCategoryCountPO> countUnreadByCategory(@Param("recipientId") Long recipientId);

    /**
     * 批量标记所有通知为已读
     */
    @Update("UPDATE notifications SET is_read = true, read_at = NOW() WHERE recipient_id = #{recipientId} AND is_read = false")
    int markAllAsRead(@Param("recipientId") Long recipientId);

    /**
     * 标记单条通知为已读
     */
    @Update("""
        UPDATE notifications
        SET is_read = true, read_at = NOW()
        WHERE id = #{id}
          AND recipient_id = #{recipientId}
          AND is_read = false
        """)
    int markAsRead(@Param("id") Long id, @Param("recipientId") Long recipientId);
}
