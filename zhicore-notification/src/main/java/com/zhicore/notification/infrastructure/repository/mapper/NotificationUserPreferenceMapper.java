package com.zhicore.notification.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationUserPreferencePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户通知偏好 Mapper。
 */
@Mapper
public interface NotificationUserPreferenceMapper extends BaseMapper<NotificationUserPreferencePO> {

    @Insert("""
        INSERT INTO notification_user_preference (
            user_id, like_enabled, comment_enabled, follow_enabled, reply_enabled, system_enabled
        ) VALUES (
            #{userId}, #{likeEnabled}, #{commentEnabled}, #{followEnabled}, #{replyEnabled}, #{systemEnabled}
        )
        ON CONFLICT (user_id) DO UPDATE SET
            like_enabled = EXCLUDED.like_enabled,
            comment_enabled = EXCLUDED.comment_enabled,
            follow_enabled = EXCLUDED.follow_enabled,
            reply_enabled = EXCLUDED.reply_enabled,
            system_enabled = EXCLUDED.system_enabled,
            updated_at = NOW()
        """)
    int upsert(NotificationUserPreferencePO po);
}
