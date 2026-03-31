package com.zhicore.notification.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("notification_campaign_shard")
public class NotificationCampaignShardPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long campaignId;

    private OffsetDateTime afterCreatedAt;

    private Long afterFollowerId;

    private Integer batchSize;

    private String status;

    private String errorMessage;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime completedAt;
}
