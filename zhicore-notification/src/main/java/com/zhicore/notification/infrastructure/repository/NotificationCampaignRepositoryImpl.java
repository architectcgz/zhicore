package com.zhicore.notification.infrastructure.repository;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.notification.domain.model.NotificationCampaign;
import com.zhicore.notification.domain.model.NotificationCampaignStatus;
import com.zhicore.notification.domain.repository.NotificationCampaignRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationCampaignMapper;
import com.zhicore.notification.infrastructure.repository.po.NotificationCampaignPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationCampaignRepositoryImpl implements NotificationCampaignRepository {

    private final NotificationCampaignMapper mapper;

    @Override
    public boolean saveIfAbsent(NotificationCampaign campaign) {
        return mapper.insertIgnore(toPO(campaign)) > 0;
    }

    @Override
    public Optional<NotificationCampaign> findByTriggerEventId(String triggerEventId) {
        return Optional.ofNullable(mapper.selectByTriggerEventId(triggerEventId)).map(this::toDomain);
    }

    @Override
    public Optional<NotificationCampaign> findById(Long campaignId) {
        return Optional.ofNullable(mapper.selectById(campaignId)).map(this::toDomain);
    }

    @Override
    public void update(NotificationCampaign campaign) {
        mapper.updateState(toPO(campaign));
    }

    private NotificationCampaignPO toPO(NotificationCampaign campaign) {
        NotificationCampaignPO po = new NotificationCampaignPO();
        po.setId(campaign.getId());
        po.setTriggerEventId(campaign.getTriggerEventId());
        po.setCampaignType("POST_PUBLISHED");
        po.setPostId(campaign.getPostId());
        po.setAuthorId(campaign.getAuthorId());
        po.setStatus(campaign.getStatus().name());
        po.setErrorMessage(campaign.getErrorMessage());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(campaign.getCreatedAt()));
        po.setUpdatedAt(DateTimeUtils.toOffsetDateTime(campaign.getUpdatedAt()));
        po.setCompletedAt(DateTimeUtils.toOffsetDateTime(campaign.getCompletedAt()));
        return po;
    }

    private NotificationCampaign toDomain(NotificationCampaignPO po) {
        return NotificationCampaign.reconstitute(
                po.getId(),
                po.getTriggerEventId(),
                po.getPostId(),
                po.getAuthorId(),
                NotificationCampaignStatus.valueOf(po.getStatus()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()),
                DateTimeUtils.toLocalDateTime(po.getUpdatedAt()),
                DateTimeUtils.toLocalDateTime(po.getCompletedAt()),
                po.getErrorMessage()
        );
    }
}
