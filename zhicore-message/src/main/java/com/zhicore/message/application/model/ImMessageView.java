package com.zhicore.message.application.model;

import com.zhicore.message.domain.model.MessageStatus;
import com.zhicore.message.domain.model.MessageType;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 统一的消息查询结果。
 *
 * application 层只依赖该模型，不依赖 provider 专属消息 DTO。
 */
@Getter
@Builder
public class ImMessageView {

    /**
     * 本地 projection 消息 ID。
     */
    private final Long localMessageId;

    /**
     * 本地 projection 会话 ID。
     */
    private final Long localConversationId;

    private final Long senderId;

    private final Long receiverId;

    private final MessageType type;

    private final String content;

    private final String mediaUrl;

    private final boolean read;

    private final OffsetDateTime readAt;

    private final MessageStatus status;

    private final OffsetDateTime createdAt;
}
