package com.zhicore.notification.application.service.preference;

import com.zhicore.notification.application.dto.NotificationUserDndDTO;
import com.zhicore.notification.application.dto.NotificationUserPreferenceDTO;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.model.NotificationUserDnd;
import com.zhicore.notification.domain.model.NotificationUserPreference;
import com.zhicore.notification.domain.repository.NotificationUserDndRepository;
import com.zhicore.notification.domain.repository.NotificationUserPreferenceRepository;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationDndRequest;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationPreferenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification preference service 测试")
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationUserPreferenceRepository preferenceRepository;

    @Mock
    private NotificationUserDndRepository dndRepository;

    private NotificationPreferenceService notificationPreferenceService;

    @BeforeEach
    void setUp() {
        notificationPreferenceService = new NotificationPreferenceService(preferenceRepository, dndRepository);
    }

    @Test
    @DisplayName("无用户偏好记录时应该返回全量启用默认值")
    void shouldReturnDefaultPreferenceWhenMissing() {
        when(preferenceRepository.findByUserId(11L)).thenReturn(Optional.empty());

        NotificationUserPreferenceDTO dto = notificationPreferenceService.getPreference(11L);

        assertTrue(dto.isLikeEnabled());
        assertTrue(dto.isCommentEnabled());
        assertTrue(dto.isFollowEnabled());
        assertTrue(dto.isReplyEnabled());
        assertTrue(dto.isSystemEnabled());
    }

    @Test
    @DisplayName("更新偏好时应该写入仓储并返回更新结果")
    void shouldSavePreferenceWhenUpdate() {
        UpdateNotificationPreferenceRequest request = new UpdateNotificationPreferenceRequest();
        request.setLikeEnabled(false);
        request.setCommentEnabled(true);
        request.setFollowEnabled(true);
        request.setReplyEnabled(true);
        request.setSystemEnabled(true);

        NotificationUserPreference stored = NotificationUserPreference.of(11L, false, true, true, true, true);
        when(preferenceRepository.findByUserId(11L)).thenReturn(Optional.of(stored));

        NotificationUserPreferenceDTO dto = notificationPreferenceService.updatePreference(11L, request);

        verify(preferenceRepository).saveOrUpdate(any(NotificationUserPreference.class));
        assertFalse(dto.isLikeEnabled());
        assertTrue(dto.isCommentEnabled());
    }

    @Test
    @DisplayName("启用跨天免打扰且当前时间在区间内时应该拒绝发送")
    void shouldRejectSendWhenWithinCrossDayDndWindow() {
        when(preferenceRepository.findByUserId(11L))
                .thenReturn(Optional.of(NotificationUserPreference.defaults(11L)));
        when(dndRepository.findByUserId(11L))
                .thenReturn(Optional.of(NotificationUserDnd.of(11L, true, "22:00", "08:00", "Asia/Shanghai")));

        boolean allowed = notificationPreferenceService.shouldSend(11L, NotificationType.LIKE, LocalTime.of(23, 30));

        assertFalse(allowed);
    }

    @Test
    @DisplayName("更新免打扰配置时应该写入仓储并返回结果")
    void shouldSaveDndWhenUpdate() {
        UpdateNotificationDndRequest request = new UpdateNotificationDndRequest();
        request.setEnabled(true);
        request.setStartTime("21:00");
        request.setEndTime("07:30");
        request.setTimezone("Asia/Shanghai");

        when(dndRepository.findByUserId(11L))
                .thenReturn(Optional.of(NotificationUserDnd.of(11L, true, "21:00", "07:30", "Asia/Shanghai")));

        NotificationUserDndDTO dto = notificationPreferenceService.updateDnd(11L, request);

        verify(dndRepository).saveOrUpdate(any(NotificationUserDnd.class));
        assertTrue(dto.isEnabled());
    }
}
