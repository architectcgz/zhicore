package com.zhicore.notification.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.model.NotificationDeliveryStatus;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationDeliveryMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationDeliveryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepository {

    private final NotificationDeliveryMapper mapper;

    @Override
    public boolean saveIfAbsent(NotificationDelivery delivery) {
        return mapper.insertIgnore(toPO(delivery)) > 0;
    }

    @Override
    public Optional<NotificationDelivery> findById(Long deliveryId) {
        return Optional.ofNullable(mapper.selectById(deliveryId)).map(this::toDomain);
    }

    @Override
    public Optional<NotificationDelivery> findByDedupeKey(String dedupeKey) {
        return Optional.ofNullable(mapper.selectByDedupeKey(dedupeKey)).map(this::toDomain);
    }

    @Override
    public List<NotificationDelivery> query(Long campaignId,
                                            Long recipientId,
                                            String channel,
                                            String status,
                                            int page,
                                            int size) {
        Page<NotificationDeliveryPO> pageRequest = new Page<>(page + 1L, size);
        LambdaQueryWrapper<NotificationDeliveryPO> wrapper = buildQueryWrapper(
                campaignId, recipientId, channel, status);
        wrapper.orderByDesc(NotificationDeliveryPO::getCreatedAt)
                .orderByDesc(NotificationDeliveryPO::getId);
        return mapper.selectPage(pageRequest, wrapper).getRecords().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long count(Long campaignId, Long recipientId, String channel, String status) {
        return mapper.selectCount(buildQueryWrapper(campaignId, recipientId, channel, status));
    }

    @Override
    public void update(NotificationDelivery delivery) {
        mapper.updateState(toPO(delivery));
    }

    private LambdaQueryWrapper<NotificationDeliveryPO> buildQueryWrapper(Long campaignId,
                                                                         Long recipientId,
                                                                         String channel,
                                                                         String status) {
        LambdaQueryWrapper<NotificationDeliveryPO> wrapper = new LambdaQueryWrapper<>();
        if (campaignId != null) {
            wrapper.eq(NotificationDeliveryPO::getCampaignId, campaignId);
        }
        if (recipientId != null) {
            wrapper.eq(NotificationDeliveryPO::getRecipientId, recipientId);
        }
        if (StringUtils.hasText(channel)) {
            wrapper.eq(NotificationDeliveryPO::getChannel, channel);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(NotificationDeliveryPO::getStatus, status);
        }
        return wrapper;
    }

    private NotificationDeliveryPO toPO(NotificationDelivery delivery) {
        NotificationDeliveryPO po = new NotificationDeliveryPO();
        po.setId(delivery.getId());
        po.setCampaignId(delivery.getCampaignId());
        po.setShardId(delivery.getShardId());
        po.setRecipientId(delivery.getRecipientId());
        po.setChannel(delivery.getChannel());
        po.setDedupeKey(delivery.getDedupeKey());
        po.setStatus(delivery.getStatus().name());
        po.setNotificationId(delivery.getNotificationId());
        po.setSkipReason(delivery.getSkipReason());
        po.setFailureReason(delivery.getFailureReason());
        po.setRetryCount(delivery.getRetryCount());
        po.setLastAttemptAt(DateTimeUtils.toOffsetDateTime(delivery.getLastAttemptAt()));
        po.setNextRetryAt(DateTimeUtils.toOffsetDateTime(delivery.getNextRetryAt()));
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(delivery.getCreatedAt()));
        po.setUpdatedAt(DateTimeUtils.toOffsetDateTime(delivery.getUpdatedAt()));
        po.setSentAt(DateTimeUtils.toOffsetDateTime(delivery.getSentAt()));
        return po;
    }

    private NotificationDelivery toDomain(NotificationDeliveryPO po) {
        return NotificationDelivery.reconstitute(
                po.getId(),
                po.getCampaignId(),
                po.getShardId(),
                po.getRecipientId(),
                po.getChannel(),
                po.getDedupeKey(),
                NotificationDeliveryStatus.valueOf(po.getStatus()),
                po.getNotificationId(),
                po.getSkipReason(),
                po.getFailureReason(),
                po.getRetryCount(),
                DateTimeUtils.toLocalDateTime(po.getLastAttemptAt()),
                DateTimeUtils.toLocalDateTime(po.getNextRetryAt()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()),
                DateTimeUtils.toLocalDateTime(po.getUpdatedAt()),
                DateTimeUtils.toLocalDateTime(po.getSentAt())
        );
    }
}
