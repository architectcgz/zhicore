package com.zhicore.notification.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通知仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMapper notificationMapper;

    @Override
    public void save(Notification notification) {
        NotificationPO po = toPO(notification);
        notificationMapper.insert(po);
    }

    @Override
    public boolean saveIfAbsent(Notification notification) {
        NotificationPO po = toPO(notification);
        return notificationMapper.insertIgnore(po) > 0;
    }

    @Override
    public Optional<Notification> findById(Long id) {
        NotificationPO po = notificationMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<Notification> findByRecipientId(String recipientId, int page, int size) {
        LambdaQueryWrapper<NotificationPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationPO::getRecipientId, recipientId)
                .orderByDesc(NotificationPO::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + (page * size));
        
        return notificationMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<AggregatedNotificationDTO> findAggregatedNotifications(Long recipientId, int page, int size) {
        int offset = page * size;
        List<AggregatedNotificationDTO> results = notificationMapper.findAggregatedNotifications(recipientId, offset, size);
        
        // 转换类型码为枚举
        for (AggregatedNotificationDTO dto : results) {
            // 类型已经在SQL中作为整数返回，需要转换
            // 由于MyBatis返回的是Integer，这里需要处理
        }
        
        return results;
    }

    @Override
    public int countAggregatedGroups(Long recipientId) {
        return notificationMapper.countAggregatedGroups(recipientId);
    }

    @Override
    public Optional<AggregatedNotificationDTO> findAggregatedNotificationByGroup(Long recipientId,
                                                                                  NotificationType type,
                                                                                  String targetType,
                                                                                  String targetId) {
        return Optional.ofNullable(notificationMapper.findAggregatedNotificationByGroup(
                recipientId, type.getCode(), targetType, targetId));
    }

    @Override
    public List<Notification> findByGroup(Long recipientId, NotificationType type,
                                          String targetType, String targetId, int limit) {
        List<NotificationPO> poList = notificationMapper.findByGroup(
                recipientId, type.getCode(), targetType, targetId, limit);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countUnread(Long recipientId) {
        return notificationMapper.countUnread(recipientId);
    }

    @Override
    public void markAsRead(Long id, Long recipientId) {
        notificationMapper.markAsRead(id, recipientId);
    }

    @Override
    public void markAllAsRead(Long recipientId) {
        notificationMapper.markAllAsRead(recipientId);
    }

    @Override
    public void delete(Long id) {
        notificationMapper.deleteById(id);
    }

    // ==================== 转换方法 ====================

    private NotificationPO toPO(Notification notification) {
        NotificationPO po = new NotificationPO();
        po.setId(notification.getId());
        po.setRecipientId(notification.getRecipientId());
        po.setType(notification.getType().getCode());
        po.setActorId(notification.getActorId());
        po.setTargetType(notification.getTargetType());
        po.setTargetId(notification.getTargetId());
        po.setContent(notification.getContent());
        po.setIsRead(notification.isRead());
        po.setReadAt(DateTimeUtils.toOffsetDateTime(notification.getReadAt()));
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(notification.getCreatedAt()));
        return po;
    }

    private Notification toDomain(NotificationPO po) {
        return Notification.reconstitute(
                po.getId(),
                po.getRecipientId(),
                NotificationType.fromCode(po.getType()),
                po.getActorId(),
                po.getTargetType(),
                po.getTargetId(),
                po.getContent(),
                po.getIsRead(),
                DateTimeUtils.toLocalDateTime(po.getReadAt()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt())
        );
    }
}
