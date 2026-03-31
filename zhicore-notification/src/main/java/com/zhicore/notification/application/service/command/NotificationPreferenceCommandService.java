package com.zhicore.notification.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.domain.model.AuthorSubscriptionLevel;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.model.UserAuthorSubscription;
import com.zhicore.notification.domain.model.UserNotificationDnd;
import com.zhicore.notification.domain.model.UserNotificationPreference;
import com.zhicore.notification.domain.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Set;

/**
 * 通知偏好写服务
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceCommandService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Transactional
    public void updateNotificationPreference(Long userId,
                                             NotificationType notificationType,
                                             NotificationChannel channel,
                                             boolean enabled) {
        validateUserId(userId);
        if (channel == NotificationChannel.SMS && enabled) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前阶段不支持启用SMS通知");
        }
        UserNotificationPreference preference = UserNotificationPreference.of(userId, notificationType, channel, enabled);
        notificationPreferenceRepository.upsertPreference(preference);
    }

    @Transactional
    public void updateNotificationDnd(Long userId,
                                      boolean enabled,
                                      LocalTime startTime,
                                      LocalTime endTime,
                                      Set<NotificationCategory> categories,
                                      Set<NotificationChannel> channels) {
        validateUserId(userId);
        UserNotificationDnd dnd = UserNotificationDnd.of(userId, enabled, startTime, endTime, categories, channels);
        notificationPreferenceRepository.upsertDnd(dnd);
    }

    @Transactional
    public void updateAuthorSubscription(Long userId,
                                         Long authorId,
                                         AuthorSubscriptionLevel level,
                                         boolean inAppEnabled,
                                         boolean websocketEnabled,
                                         boolean emailEnabled,
                                         boolean digestEnabled) {
        validateUserId(userId);
        if (authorId == null || authorId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作者ID必须为正数");
        }
        UserAuthorSubscription subscription = UserAuthorSubscription.of(
                userId,
                authorId,
                level,
                inAppEnabled,
                websocketEnabled,
                emailEnabled,
                digestEnabled
        );
        notificationPreferenceRepository.upsertAuthorSubscription(subscription);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID必须为正数");
        }
    }
}
