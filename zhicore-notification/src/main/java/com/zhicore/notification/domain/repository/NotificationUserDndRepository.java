package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationUserDnd;

import java.util.Optional;

/**
 * 用户免打扰仓储。
 */
public interface NotificationUserDndRepository {

    Optional<NotificationUserDnd> findByUserId(Long userId);

    void saveOrUpdate(NotificationUserDnd dnd);
}
