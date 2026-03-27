package com.zhicore.notification.infrastructure.repository;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.notification.domain.model.NotificationCampaignShard;
import com.zhicore.notification.domain.model.NotificationCampaignShardStatus;
import com.zhicore.notification.domain.repository.NotificationCampaignShardRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationCampaignShardMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationCampaignShardPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationCampaignShardRepositoryImpl implements NotificationCampaignShardRepository {

    private final NotificationCampaignShardMapper mapper;

    @Override
    public void save(NotificationCampaignShard shard) {
        mapper.insert(toPO(shard));
    }

    @Override
    public Optional<NotificationCampaignShard> findNextPending(Long campaignId) {
        return Optional.ofNullable(mapper.selectNextPending(campaignId)).map(this::toDomain);
    }

    @Override
    public int countPending(Long campaignId) {
        return mapper.countPending(campaignId);
    }

    @Override
    public void update(NotificationCampaignShard shard) {
        mapper.updateState(toPO(shard));
    }

    private NotificationCampaignShardPO toPO(NotificationCampaignShard shard) {
        NotificationCampaignShardPO po = new NotificationCampaignShardPO();
        po.setId(shard.getId());
        po.setCampaignId(shard.getCampaignId());
        po.setAfterCreatedAt(DateTimeUtils.toOffsetDateTime(shard.getAfterCreatedAt()));
        po.setAfterFollowerId(shard.getAfterFollowerId());
        po.setBatchSize(shard.getBatchSize());
        po.setStatus(shard.getStatus().name());
        po.setErrorMessage(shard.getErrorMessage());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(shard.getCreatedAt()));
        po.setUpdatedAt(DateTimeUtils.toOffsetDateTime(shard.getUpdatedAt()));
        po.setCompletedAt(DateTimeUtils.toOffsetDateTime(shard.getCompletedAt()));
        return po;
    }

    private NotificationCampaignShard toDomain(NotificationCampaignShardPO po) {
        return NotificationCampaignShard.reconstitute(
                po.getId(),
                po.getCampaignId(),
                DateTimeUtils.toLocalDateTime(po.getAfterCreatedAt()),
                po.getAfterFollowerId(),
                po.getBatchSize(),
                NotificationCampaignShardStatus.valueOf(po.getStatus()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()),
                DateTimeUtils.toLocalDateTime(po.getUpdatedAt()),
                DateTimeUtils.toLocalDateTime(po.getCompletedAt()),
                po.getErrorMessage()
        );
    }
}
