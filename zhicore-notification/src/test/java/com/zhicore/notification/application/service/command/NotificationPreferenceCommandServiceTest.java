package com.zhicore.notification.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.notification.application.service.query.NotificationPreferenceQueryService;
import com.zhicore.notification.domain.model.AuthorSubscriptionLevel;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.model.UserAuthorSubscription;
import com.zhicore.notification.domain.model.UserNotificationDnd;
import com.zhicore.notification.domain.model.UserNotificationPreference;
import com.zhicore.notification.domain.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification preference service 测试")
class NotificationPreferenceCommandServiceTest {

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    private NotificationPreferenceCommandService notificationPreferenceCommandService;
    private NotificationPreferenceQueryService notificationPreferenceQueryService;

    @BeforeEach
    void setUp() {
        notificationPreferenceCommandService = new NotificationPreferenceCommandService(notificationPreferenceRepository);
        notificationPreferenceQueryService = new NotificationPreferenceQueryService(notificationPreferenceRepository);
    }

    @Test
    @DisplayName("更新通知偏好时应该持久化类型渠道开关")
    void updateNotificationPreference_shouldPersistPreference() {
        notificationPreferenceCommandService.updateNotificationPreference(
                11L,
                NotificationType.POST_COMMENTED,
                NotificationChannel.EMAIL,
                false
        );

        verify(notificationPreferenceRepository).upsertPreference(argThat(preference ->
                preference.getUserId().equals(11L)
                        && preference.getNotificationType() == NotificationType.POST_COMMENTED
                        && preference.getChannel() == NotificationChannel.EMAIL
                        && !preference.isEnabled()));
    }

    @Test
    @DisplayName("非法用户ID更新作者订阅时应该拒绝")
    void updateAuthorSubscription_shouldRejectInvalidUserId() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                notificationPreferenceCommandService.updateAuthorSubscription(
                        0L,
                        22L,
                        AuthorSubscriptionLevel.ALL,
                        true,
                        true,
                        false,
                        false
                ));

        assertTrue(exception.getMessage().contains("用户ID"));
    }

    @Test
    @DisplayName("quiet hours 命中时应该只关闭命中的推送渠道")
    void resolveChannels_shouldDisablePushWhenTypeDisabledInDndWindow() {
        when(notificationPreferenceRepository.findPreferencesByUserId(11L)).thenReturn(List.of(
                UserNotificationPreference.of(
                        11L,
                        NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                        NotificationChannel.WEBSOCKET,
                        true
                )
        ));
        when(notificationPreferenceRepository.findUserDnd(11L)).thenReturn(Optional.of(
                UserNotificationDnd.of(
                        11L,
                        true,
                        LocalTime.of(22, 0),
                        LocalTime.of(8, 0),
                        EnumSet.of(NotificationCategory.CONTENT),
                        EnumSet.of(NotificationChannel.WEBSOCKET)
                )
        ));

        NotificationPreferenceQueryService.ChannelDecision decision =
                notificationPreferenceQueryService.resolveChannels(
                        11L,
                        NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                        null,
                        LocalTime.of(23, 30)
                );

        assertTrue(decision.isChannelEnabled(NotificationChannel.IN_APP));
        assertFalse(decision.isChannelEnabled(NotificationChannel.WEBSOCKET));
    }

    @Test
    @DisplayName("作者订阅静默时应该关闭所有即时渠道")
    void resolveChannels_shouldDisableAllRealtimeChannelsWhenAuthorMuted() {
        when(notificationPreferenceRepository.findPreferencesByUserId(11L)).thenReturn(List.of());
        when(notificationPreferenceRepository.findUserDnd(11L)).thenReturn(Optional.empty());
        when(notificationPreferenceRepository.findAuthorSubscription(11L, 22L)).thenReturn(Optional.of(
                UserAuthorSubscription.of(
                        11L,
                        22L,
                        AuthorSubscriptionLevel.MUTED,
                        false,
                        false,
                        false,
                        false
                )
        ));

        NotificationPreferenceQueryService.ChannelDecision decision =
                notificationPreferenceQueryService.resolveChannels(
                        11L,
                        NotificationType.POST_PUBLISHED_BY_FOLLOWING,
                        22L,
                        LocalTime.NOON
                );

        assertFalse(decision.isChannelEnabled(NotificationChannel.IN_APP));
        assertFalse(decision.isChannelEnabled(NotificationChannel.WEBSOCKET));
        assertFalse(decision.isChannelEnabled(NotificationChannel.EMAIL));
    }
}
