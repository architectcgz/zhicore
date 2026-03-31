package com.zhicore.notification.infrastructure.repository.mapper;

import com.zhicore.notification.infrastructure.repository.po.UserAuthorSubscriptionPO;
import com.zhicore.notification.infrastructure.repository.po.UserNotificationDndPO;
import com.zhicore.notification.infrastructure.repository.po.UserNotificationPreferencePO;
import com.zhicore.notification.infrastructure.repository.typehandler.StringArrayTypeHandler;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通知偏好 Mapper
 */
@Mapper
public interface NotificationPreferenceMapper {

    @Select("""
        SELECT user_id AS userId, notification_type AS notificationType, channel, enabled
        FROM notification_user_preference
        WHERE user_id = #{userId}
        ORDER BY notification_type, channel
        """)
    List<UserNotificationPreferencePO> selectPreferencesByUserId(@Param("userId") Long userId);

    @Insert("""
        INSERT INTO notification_user_preference (user_id, notification_type, channel, enabled)
        VALUES (#{userId}, #{notificationType}, #{channel}, #{enabled})
        ON CONFLICT (user_id, notification_type, channel)
        DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = CURRENT_TIMESTAMP
        """)
    int upsertPreference(UserNotificationPreferencePO po);

    @Select("""
        SELECT user_id AS userId,
               enabled,
               start_time AS startTime,
               end_time AS endTime,
               categories,
               channels
        FROM notification_user_dnd
        WHERE user_id = #{userId}
        """)
    @Results({
            @Result(property = "categories", column = "categories", typeHandler = StringArrayTypeHandler.class),
            @Result(property = "channels", column = "channels", typeHandler = StringArrayTypeHandler.class)
    })
    UserNotificationDndPO selectDndByUserId(@Param("userId") Long userId);

    @Insert("""
        INSERT INTO notification_user_dnd (user_id, enabled, start_time, end_time, categories, channels)
        VALUES (
            #{userId},
            #{enabled},
            #{startTime},
            #{endTime},
            #{categories, typeHandler=com.zhicore.notification.infrastructure.repository.typehandler.StringArrayTypeHandler},
            #{channels, typeHandler=com.zhicore.notification.infrastructure.repository.typehandler.StringArrayTypeHandler}
        )
        ON CONFLICT (user_id)
        DO UPDATE SET enabled = EXCLUDED.enabled,
                      start_time = EXCLUDED.start_time,
                      end_time = EXCLUDED.end_time,
                      categories = EXCLUDED.categories,
                      channels = EXCLUDED.channels,
                      updated_at = CURRENT_TIMESTAMP
        """)
    int upsertDnd(UserNotificationDndPO po);

    @Select("""
        SELECT user_id AS userId,
               author_id AS authorId,
               subscription_level AS subscriptionLevel,
               in_app_enabled AS inAppEnabled,
               websocket_enabled AS websocketEnabled,
               email_enabled AS emailEnabled,
               digest_enabled AS digestEnabled
        FROM notification_author_subscription
        WHERE user_id = #{userId} AND author_id = #{authorId}
        """)
    UserAuthorSubscriptionPO selectAuthorSubscription(@Param("userId") Long userId, @Param("authorId") Long authorId);

    @Insert("""
        INSERT INTO notification_author_subscription (
            user_id, author_id, subscription_level, in_app_enabled, websocket_enabled, email_enabled, digest_enabled
        ) VALUES (
            #{userId}, #{authorId}, #{subscriptionLevel}, #{inAppEnabled}, #{websocketEnabled}, #{emailEnabled}, #{digestEnabled}
        )
        ON CONFLICT (user_id, author_id)
        DO UPDATE SET subscription_level = EXCLUDED.subscription_level,
                      in_app_enabled = EXCLUDED.in_app_enabled,
                      websocket_enabled = EXCLUDED.websocket_enabled,
                      email_enabled = EXCLUDED.email_enabled,
                      digest_enabled = EXCLUDED.digest_enabled,
                      updated_at = CURRENT_TIMESTAMP
        """)
    int upsertAuthorSubscription(UserAuthorSubscriptionPO po);
}
