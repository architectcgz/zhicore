package com.zhicore.notification.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("notification_campaign")
public class NotificationCampaignPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String triggerEventId;

    private String campaignType;

    private Long postId;

    private Long authorId;

    private String status;

    private String errorMessage;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime completedAt;
}
