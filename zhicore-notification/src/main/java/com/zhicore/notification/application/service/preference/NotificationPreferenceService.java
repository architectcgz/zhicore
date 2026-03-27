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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalTime;

/**
 * 通知偏好与免打扰服务。
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationUserPreferenceRepository preferenceRepository;
    private final NotificationUserDndRepository dndRepository;
    @Value("${notification.preference.default-timezone:Asia/Shanghai}")
    private String defaultTimezone;

    public NotificationUserPreferenceDTO getPreference(Long userId) {
        Assert.notNull(userId, "用户ID不能为空");
        return toPreferenceDTO(preferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationUserPreference.defaults(userId)));
    }

    @Transactional
    public NotificationUserPreferenceDTO updatePreference(Long userId, UpdateNotificationPreferenceRequest request) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(request, "偏好请求不能为空");
        NotificationUserPreference preference = NotificationUserPreference.of(
                userId,
                Boolean.TRUE.equals(request.getLikeEnabled()),
                Boolean.TRUE.equals(request.getCommentEnabled()),
                Boolean.TRUE.equals(request.getFollowEnabled()),
                Boolean.TRUE.equals(request.getReplyEnabled()),
                Boolean.TRUE.equals(request.getSystemEnabled())
        );
        preferenceRepository.saveOrUpdate(preference);
        return getPreference(userId);
    }

    public NotificationUserDndDTO getDnd(Long userId) {
        Assert.notNull(userId, "用户ID不能为空");
        return toDndDTO(dndRepository.findByUserId(userId)
                .orElseGet(() -> NotificationUserDnd.defaults(userId, defaultTimezone)));
    }

    @Transactional
    public NotificationUserDndDTO updateDnd(Long userId, UpdateNotificationDndRequest request) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(request, "免打扰请求不能为空");
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        NotificationUserDnd dnd = NotificationUserDnd.of(
                userId,
                enabled,
                enabled ? request.getStartTime() : null,
                enabled ? request.getEndTime() : null,
                StringUtils.hasText(request.getTimezone()) ? request.getTimezone() : defaultTimezone
        );
        dndRepository.saveOrUpdate(dnd);
        return getDnd(userId);
    }

    public boolean shouldSend(Long userId, NotificationType type, LocalTime now) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(type, "通知类型不能为空");
        NotificationUserPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationUserPreference.defaults(userId));
        if (!preference.supports(type)) {
            return false;
        }

        NotificationUserDnd dnd = dndRepository.findByUserId(userId)
                .orElseGet(() -> NotificationUserDnd.defaults(userId, defaultTimezone));
        return !dnd.isActive(now);
    }

    private NotificationUserPreferenceDTO toPreferenceDTO(NotificationUserPreference preference) {
        return NotificationUserPreferenceDTO.builder()
                .likeEnabled(preference.isLikeEnabled())
                .commentEnabled(preference.isCommentEnabled())
                .followEnabled(preference.isFollowEnabled())
                .replyEnabled(preference.isReplyEnabled())
                .systemEnabled(preference.isSystemEnabled())
                .build();
    }

    private NotificationUserDndDTO toDndDTO(NotificationUserDnd dnd) {
        return NotificationUserDndDTO.builder()
                .enabled(dnd.isEnabled())
                .startTime(dnd.getStartTime())
                .endTime(dnd.getEndTime())
                .timezone(dnd.getTimezone())
                .build();
    }
}
