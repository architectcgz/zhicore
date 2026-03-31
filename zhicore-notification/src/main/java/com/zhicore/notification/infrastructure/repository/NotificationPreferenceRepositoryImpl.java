package com.zhicore.notification.infrastructure.repository;

import com.zhicore.notification.domain.model.AuthorSubscriptionLevel;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.model.UserAuthorSubscription;
import com.zhicore.notification.domain.model.UserNotificationDnd;
import com.zhicore.notification.domain.model.UserNotificationPreference;
import com.zhicore.notification.domain.repository.NotificationPreferenceRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationPreferenceMapper;
import com.zhicore.notification.infrastructure.repository.po.UserAuthorSubscriptionPO;
import com.zhicore.notification.infrastructure.repository.po.UserNotificationDndPO;
import com.zhicore.notification.infrastructure.repository.po.UserNotificationPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * 通知偏好仓储实现
 */
@Repository
@RequiredArgsConstructor
public class NotificationPreferenceRepositoryImpl implements NotificationPreferenceRepository {

    private final NotificationPreferenceMapper notificationPreferenceMapper;

    @Override
    public List<UserNotificationPreference> findPreferencesByUserId(Long userId) {
        return notificationPreferenceMapper.selectPreferencesByUserId(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void upsertPreference(UserNotificationPreference preference) {
        notificationPreferenceMapper.upsertPreference(toPO(preference));
    }

    @Override
    public Optional<UserNotificationDnd> findUserDnd(Long userId) {
        return Optional.ofNullable(notificationPreferenceMapper.selectDndByUserId(userId))
                .map(this::toDomain);
    }

    @Override
    public void upsertDnd(UserNotificationDnd dnd) {
        notificationPreferenceMapper.upsertDnd(toPO(dnd));
    }

    @Override
    public Optional<UserAuthorSubscription> findAuthorSubscription(Long userId, Long authorId) {
        return Optional.ofNullable(notificationPreferenceMapper.selectAuthorSubscription(userId, authorId))
                .map(this::toDomain);
    }

    @Override
    public void upsertAuthorSubscription(UserAuthorSubscription subscription) {
        notificationPreferenceMapper.upsertAuthorSubscription(toPO(subscription));
    }

    private UserNotificationPreference toDomain(UserNotificationPreferencePO po) {
        return UserNotificationPreference.of(
                po.getUserId(),
                NotificationType.valueOf(po.getNotificationType()),
                NotificationChannel.fromValue(po.getChannel()),
                Boolean.TRUE.equals(po.getEnabled())
        );
    }

    private UserNotificationDnd toDomain(UserNotificationDndPO po) {
        EnumSet<NotificationCategory> categories = EnumSet.noneOf(NotificationCategory.class);
        if (po.getCategories() != null) {
            po.getCategories().forEach(value -> categories.add(NotificationCategory.fromValue(value)));
        }
        EnumSet<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (po.getChannels() != null) {
            po.getChannels().forEach(value -> channels.add(NotificationChannel.fromValue(value)));
        }
        return UserNotificationDnd.of(
                po.getUserId(),
                Boolean.TRUE.equals(po.getEnabled()),
                po.getStartTime(),
                po.getEndTime(),
                categories,
                channels
        );
    }

    private UserAuthorSubscription toDomain(UserAuthorSubscriptionPO po) {
        return UserAuthorSubscription.of(
                po.getUserId(),
                po.getAuthorId(),
                AuthorSubscriptionLevel.fromValue(po.getSubscriptionLevel()),
                Boolean.TRUE.equals(po.getInAppEnabled()),
                Boolean.TRUE.equals(po.getWebsocketEnabled()),
                Boolean.TRUE.equals(po.getEmailEnabled()),
                Boolean.TRUE.equals(po.getDigestEnabled())
        );
    }

    private UserNotificationPreferencePO toPO(UserNotificationPreference preference) {
        UserNotificationPreferencePO po = new UserNotificationPreferencePO();
        po.setUserId(preference.getUserId());
        po.setNotificationType(preference.getNotificationType().name());
        po.setChannel(preference.getChannel().name());
        po.setEnabled(preference.isEnabled());
        return po;
    }

    private UserNotificationDndPO toPO(UserNotificationDnd dnd) {
        UserNotificationDndPO po = new UserNotificationDndPO();
        po.setUserId(dnd.getUserId());
        po.setEnabled(dnd.isEnabled());
        po.setStartTime(dnd.getStartTime());
        po.setEndTime(dnd.getEndTime());
        po.setCategories(dnd.getCategories().stream().map(Enum::name).toList());
        po.setChannels(dnd.getChannels().stream().map(Enum::name).toList());
        return po;
    }

    private UserAuthorSubscriptionPO toPO(UserAuthorSubscription subscription) {
        UserAuthorSubscriptionPO po = new UserAuthorSubscriptionPO();
        po.setUserId(subscription.getUserId());
        po.setAuthorId(subscription.getAuthorId());
        po.setSubscriptionLevel(subscription.getLevel().name());
        po.setInAppEnabled(subscription.isInAppEnabled());
        po.setWebsocketEnabled(subscription.isWebsocketEnabled());
        po.setEmailEnabled(subscription.isEmailEnabled());
        po.setDigestEnabled(subscription.isDigestEnabled());
        return po;
    }
}
