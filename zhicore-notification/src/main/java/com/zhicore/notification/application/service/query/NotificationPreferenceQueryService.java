package com.zhicore.notification.application.service.query;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.model.UserAuthorSubscription;
import com.zhicore.notification.domain.model.UserNotificationDnd;
import com.zhicore.notification.domain.model.UserNotificationPreference;
import com.zhicore.notification.domain.repository.NotificationPreferenceRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知偏好读服务
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceQueryService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public List<UserNotificationPreference> getPreferences(Long userId) {
        validateUserId(userId);
        return notificationPreferenceRepository.findPreferencesByUserId(userId);
    }

    public Optional<UserNotificationDnd> getDnd(Long userId) {
        validateUserId(userId);
        return notificationPreferenceRepository.findUserDnd(userId);
    }

    public UserAuthorSubscription getAuthorSubscription(Long userId, Long authorId) {
        validateUserId(userId);
        validateAuthorId(authorId);
        return notificationPreferenceRepository.findAuthorSubscription(userId, authorId)
                .orElse(UserAuthorSubscription.defaultFor(userId, authorId));
    }

    public ChannelDecision resolveChannels(Long userId,
                                          NotificationType notificationType,
                                          Long authorId,
                                          LocalTime currentTime) {
        validateUserId(userId);
        EnumMap<NotificationChannel, Boolean> states = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : NotificationChannel.values()) {
            states.put(channel, isSupportedChannel(channel));
        }

        for (UserNotificationPreference preference : notificationPreferenceRepository.findPreferencesByUserId(userId)) {
            if (preference.getNotificationType() == notificationType) {
                states.put(preference.getChannel(), isSupportedChannel(preference.getChannel()) && preference.isEnabled());
            }
        }

        if (authorId != null) {
            validateAuthorId(authorId);
            notificationPreferenceRepository.findAuthorSubscription(userId, authorId)
                    .ifPresent(subscription -> {
                        for (NotificationChannel channel : NotificationChannel.values()) {
                            states.put(channel, states.get(channel) && subscription.allowsChannel(channel));
                        }
                    });
        }

        NotificationCategory category = resolveCategory(notificationType);
        notificationPreferenceRepository.findUserDnd(userId)
                .filter(dnd -> dnd.isActiveAt(currentTime))
                .ifPresent(dnd -> {
                    for (NotificationChannel channel : NotificationChannel.values()) {
                        if (dnd.appliesTo(category, channel)) {
                            states.put(channel, false);
                        }
                    }
                });

        return new ChannelDecision(states);
    }

    public boolean isChannelEnabled(Long userId,
                                    NotificationType notificationType,
                                    Long authorId,
                                    NotificationChannel channel,
                                    LocalTime currentTime) {
        return resolveChannels(userId, notificationType, authorId, currentTime).isChannelEnabled(channel);
    }

    private NotificationCategory resolveCategory(NotificationType notificationType) {
        return switch (notificationType) {
            case POST_LIKED, POST_COMMENTED, COMMENT_REPLIED, POST_PUBLISHED_BY_FOLLOWING, POST_PUBLISHED_DIGEST,
                    LIKE, COMMENT, REPLY -> NotificationCategory.CONTENT;
            case USER_FOLLOWED, FOLLOW -> NotificationCategory.SOCIAL;
            case SYSTEM_ANNOUNCEMENT, SYSTEM -> NotificationCategory.SYSTEM;
            case SECURITY_ALERT -> NotificationCategory.SECURITY;
        };
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID必须为正数");
        }
    }

    private void validateAuthorId(Long authorId) {
        if (authorId == null || authorId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作者ID必须为正数");
        }
    }

    private boolean isSupportedChannel(NotificationChannel channel) {
        return channel != NotificationChannel.SMS;
    }

    /**
     * 渠道判定结果
     */
    @Getter
    public static class ChannelDecision {

        private final Map<NotificationChannel, Boolean> channelStates;

        ChannelDecision(Map<NotificationChannel, Boolean> channelStates) {
            this.channelStates = Map.copyOf(channelStates);
        }

        public boolean isChannelEnabled(NotificationChannel channel) {
            return Boolean.TRUE.equals(channelStates.get(channel));
        }
    }
}
