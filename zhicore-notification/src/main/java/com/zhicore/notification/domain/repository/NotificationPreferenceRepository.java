package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.UserAuthorSubscription;
import com.zhicore.notification.domain.model.UserNotificationDnd;
import com.zhicore.notification.domain.model.UserNotificationPreference;

import java.util.List;
import java.util.Optional;

/**
 * 通知偏好仓储
 */
public interface NotificationPreferenceRepository {

    List<UserNotificationPreference> findPreferencesByUserId(Long userId);

    void upsertPreference(UserNotificationPreference preference);

    Optional<UserNotificationDnd> findUserDnd(Long userId);

    void upsertDnd(UserNotificationDnd dnd);

    Optional<UserAuthorSubscription> findAuthorSubscription(Long userId, Long authorId);

    void upsertAuthorSubscription(UserAuthorSubscription subscription);
}
