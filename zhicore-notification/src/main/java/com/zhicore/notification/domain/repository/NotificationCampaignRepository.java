package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationCampaign;

import java.util.Optional;

public interface NotificationCampaignRepository {

    boolean saveIfAbsent(NotificationCampaign campaign);

    Optional<NotificationCampaign> findByTriggerEventId(String triggerEventId);

    Optional<NotificationCampaign> findById(Long campaignId);

    void update(NotificationCampaign campaign);
}
