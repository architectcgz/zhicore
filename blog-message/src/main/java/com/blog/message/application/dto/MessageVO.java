package com.blog.message.application.dto;

import com.blog.message.domain.model.MessageStatus;
import com.blog.message.domain.model.MessageType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息视图对象
 *
 * @author Blog Team
 */
@Data
@Builder
public class MessageVO {

    /**
     * 消息ID
     */
    private Long id;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 发送者昵称
     */
    private String senderNickName;

    /**
     * 发送者头像
     */
    private String senderAvatarUrl;

    /**
     * 接收者ID
     */
    private Long receiverId;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 媒体URL
     */
    private String mediaUrl;

    /**
     * 是否已读
     */
    private boolean isRead;

    /**
     * 已读时间
     */
    private LocalDateTime readAt;

    /**
     * 消息状态
     */
    private MessageStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 是否是自己发送的
     */
    private boolean isSelf;
}
