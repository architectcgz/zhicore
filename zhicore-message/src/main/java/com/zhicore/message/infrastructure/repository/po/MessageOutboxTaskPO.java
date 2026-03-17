package com.zhicore.message.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 消息 Outbox 任务持久化对象。
 */
@Data
@TableName("message_outbox_task")
public class MessageOutboxTaskPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskKey;

    private String taskType;

    private Long aggregateId;

    private String payload;

    private Integer retryCount;

    private String status;

    private String lastError;

    private OffsetDateTime nextAttemptAt;

    private String claimedBy;

    private OffsetDateTime claimedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime dispatchedAt;
}
