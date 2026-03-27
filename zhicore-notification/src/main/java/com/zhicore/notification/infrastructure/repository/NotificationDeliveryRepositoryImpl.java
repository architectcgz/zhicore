package com.zhicore.notification.infrastructure.repository;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.model.NotificationDeliveryStatus;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationDeliveryMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationDeliveryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public Optional<NotificationDelivery> findByDedupeKey(String dedupeKey) {
        return Optional.ofNullable(mapper.selectByDedupeKey(dedupeKey)).map(this::toDomain);
    }

    @Override
    public void update(NotificationDelivery delivery) {
        mapper.updateState(toPO(delivery));
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
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()),
                DateTimeUtils.toLocalDateTime(po.getUpdatedAt()),
                DateTimeUtils.toLocalDateTime(po.getSentAt())
        );
    }
}
