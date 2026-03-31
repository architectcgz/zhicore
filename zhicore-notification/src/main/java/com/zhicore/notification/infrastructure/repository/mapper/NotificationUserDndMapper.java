package com.zhicore.notification.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationUserDndPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户免打扰配置 Mapper。
 */
@Mapper
public interface NotificationUserDndMapper extends BaseMapper<NotificationUserDndPO> {

    @Insert("""
        INSERT INTO notification_user_dnd (
            user_id, enabled, start_time, end_time, timezone
        ) VALUES (
            #{userId}, #{enabled}, #{startTime}, #{endTime}, #{timezone}
        )
        ON CONFLICT (user_id) DO UPDATE SET
            enabled = EXCLUDED.enabled,
            start_time = EXCLUDED.start_time,
            end_time = EXCLUDED.end_time,
            timezone = EXCLUDED.timezone,
            updated_at = NOW()
        """)
    int upsert(NotificationUserDndPO po);
}
