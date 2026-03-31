package com.zhicore.message.application.model;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 统一的会话查询结果。
 *
 * 该模型属于 application 自有契约，不直接泄漏任一 IM provider 的 DTO。
 */
@Getter
@Builder
public class ImConversationView {

    /**
     * 本地 projection 会话 ID。
     * 当接入远端 provider 时，adapter 应尽量回填该值以保持外部 API 稳定。
     */
    private final Long localConversationId;

    /**
     * 稳定业务会话标识。
     */
    private final DirectConversationRef conversationRef;

    /**
     * 最后一条消息预览。
     */
    private final String lastMessageContent;

    /**
     * 最后一条消息时间。
     */
    private final OffsetDateTime lastMessageAt;

    /**
     * 当前用户未读数。
     */
    private final int unreadCount;

    /**
     * 会话创建时间。
     */
    private final OffsetDateTime createdAt;
}
