package com.zhicore.notification.infrastructure.repository;

import com.zhicore.notification.domain.model.NotificationUserPreference;
import com.zhicore.notification.domain.repository.NotificationUserPreferenceRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationUserPreferenceMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationUserPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户通知偏好仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class NotificationUserPreferenceRepositoryImpl implements NotificationUserPreferenceRepository {

    private final NotificationUserPreferenceMapper mapper;

    @Override
    public Optional<NotificationUserPreference> findByUserId(Long userId) {
        NotificationUserPreferencePO po = mapper.selectById(userId);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(NotificationUserPreference.of(
                po.getUserId(),
                Boolean.TRUE.equals(po.getLikeEnabled()),
                Boolean.TRUE.equals(po.getCommentEnabled()),
                Boolean.TRUE.equals(po.getFollowEnabled()),
                Boolean.TRUE.equals(po.getReplyEnabled()),
                Boolean.TRUE.equals(po.getSystemEnabled()),
                Boolean.TRUE.equals(po.getPublishEnabled())));
    }

    @Override
    public void saveOrUpdate(NotificationUserPreference preference) {
        NotificationUserPreferencePO po = new NotificationUserPreferencePO();
        po.setUserId(preference.getUserId());
        po.setLikeEnabled(preference.isLikeEnabled());
        po.setCommentEnabled(preference.isCommentEnabled());
        po.setFollowEnabled(preference.isFollowEnabled());
        po.setReplyEnabled(preference.isReplyEnabled());
        po.setSystemEnabled(preference.isSystemEnabled());
        po.setPublishEnabled(preference.isPublishEnabled());
        mapper.upsert(po);
    }
}
