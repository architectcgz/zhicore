package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationUserPreference;

import java.util.Optional;

/**
 * 用户通知偏好仓储。
 */
public interface NotificationUserPreferenceRepository {

    Optional<NotificationUserPreference> findByUserId(Long userId);

    void saveOrUpdate(NotificationUserPreference preference);
}
