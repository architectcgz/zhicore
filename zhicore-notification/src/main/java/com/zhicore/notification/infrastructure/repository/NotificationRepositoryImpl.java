package com.zhicore.notification.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.domain.model.NotificationGroupState;
import com.zhicore.notification.domain.model.NotificationImportance;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通知仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMapper notificationMapper;

    @Override
    public void save(Notification notification) {
        notificationMapper.insertOne(toPO(notification));
    }

    @Override
    public boolean saveIfAbsent(Notification notification) {
        return notificationMapper.insertIgnore(toPO(notification)) > 0;
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return Optional.ofNullable(notificationMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Notification> findByRecipientId(Long recipientId, int page, int size) {
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
        return notificationMapper.findAggregatedNotifications(recipientId, page * size, size);
    }

    @Override
    public int countAggregatedGroups(Long recipientId) {
        return notificationMapper.countAggregatedGroups(recipientId);
    }

    @Override
    public Optional<AggregatedNotificationDTO> findAggregatedNotificationByGroup(Long recipientId,
                                                                                 NotificationType type,
                                                                                 String targetType,
                                                                                 Long targetId) {
        return Optional.ofNullable(notificationMapper.findAggregatedNotificationByGroup(
                recipientId, type.getCode(), targetType, targetId
        ));
    }

    @Override
    public List<Notification> findByGroup(Long recipientId,
                                          NotificationType type,
                                          String targetType,
                                          Long targetId,
                                          int limit) {
        return notificationMapper.findByGroup(recipientId, type.getCode(), targetType, targetId, limit).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countUnread(Long recipientId) {
        return notificationMapper.countUnread(recipientId);
    }

    @Override
    public Map<Integer, Integer> countUnreadByCategory(Long recipientId) {
        return notificationMapper.countUnreadByCategory(recipientId).stream()
                .collect(Collectors.toMap(
                        item -> item.getCategory() == null ? -1 : item.getCategory(),
                        item -> item.getUnreadCount() == null ? 0 : item.getUnreadCount()
                ));
    }

    @Override
    public int markAsRead(Long id, Long recipientId) {
        return notificationMapper.markAsRead(id, recipientId);
    }

    @Override
    public int markAllAsRead(Long recipientId) {
        return notificationMapper.markAllAsRead(recipientId);
    }

    @Override
    public void delete(Long id) {
        notificationMapper.deleteById(id);
    }

    private NotificationPO toPO(Notification notification) {
        NotificationPO po = new NotificationPO();
        po.setId(notification.getId());
        po.setRecipientId(notification.getRecipientId());
        po.setType(notification.getType().getCode());
        po.setCategory(notification.getCategoryEnum().getCode());
        po.setEventCode(notification.getEventCode());
        po.setMetadata(notification.getMetadata());
        po.setNotificationType(notification.getType().name());
        po.setActorId(notification.getActorId());
        po.setTargetType(notification.getTargetType());
        po.setTargetId(notification.getTargetId());
        po.setSourceEventId(notification.getSourceEventId());
        po.setGroupKey(NotificationGroupState.resolveGroupKey(notification));
        po.setPayloadJson(notification.getPayloadJson());
        po.setContent(notification.getContent());
        po.setImportance(notification.getImportance().getCode());
        po.setIsRead(notification.isRead());
        po.setReadAt(notification.getReadAt());
        po.setCreatedAt(notification.getCreatedAt());
        return po;
    }

    private Notification toDomain(NotificationPO po) {
        return Notification.reconstitute(
                po.getId(),
                po.getRecipientId(),
                NotificationType.fromValue(po.getNotificationType(), po.getType()),
                po.getCategory() != null ? NotificationCategory.fromCode(po.getCategory()).name() : null,
                po.getEventCode(),
                po.getMetadata(),
                po.getActorId(),
                po.getTargetType(),
                po.getTargetId(),
                po.getSourceEventId(),
                po.getGroupKey(),
                po.getPayloadJson(),
                po.getImportance() != null ? NotificationImportance.fromCode(po.getImportance()) : null,
                po.getContent(),
                Boolean.TRUE.equals(po.getIsRead()),
                po.getReadAt(),
                po.getCreatedAt()
        );
    }
}
