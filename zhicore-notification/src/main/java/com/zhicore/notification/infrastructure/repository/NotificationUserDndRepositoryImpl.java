package com.zhicore.notification.infrastructure.repository;

import com.zhicore.notification.domain.model.NotificationUserDnd;
import com.zhicore.notification.domain.repository.NotificationUserDndRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationUserDndMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationUserDndPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户免打扰仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class NotificationUserDndRepositoryImpl implements NotificationUserDndRepository {

    private final NotificationUserDndMapper mapper;

    @Override
    public Optional<NotificationUserDnd> findByUserId(Long userId) {
        NotificationUserDndPO po = mapper.selectById(userId);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(NotificationUserDnd.of(
                po.getUserId(),
                Boolean.TRUE.equals(po.getEnabled()),
                po.getStartTime(),
                po.getEndTime(),
                po.getTimezone()));
    }

    @Override
    public void saveOrUpdate(NotificationUserDnd dnd) {
        NotificationUserDndPO po = new NotificationUserDndPO();
        po.setUserId(dnd.getUserId());
        po.setEnabled(dnd.isEnabled());
        po.setStartTime(dnd.getStartTime());
        po.setEndTime(dnd.getEndTime());
        po.setTimezone(dnd.getTimezone());
        mapper.upsert(po);
    }
}
