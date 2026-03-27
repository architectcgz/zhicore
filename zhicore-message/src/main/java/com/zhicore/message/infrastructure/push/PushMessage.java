package com.zhicore.message.infrastructure.push;

import com.zhicore.message.domain.model.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 推送消息
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private Long messageId;

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
     * 消息类型
     */
    private MessageType type;

    /**
     * 消息内容预览
     */
    private String contentPreview;

    /**
     * 发送时间
     */
    private LocalDateTime sentAt;

    /**
     * 推送类型
     */
    private PushType pushType;

    /**
     * 推送类型枚举
     */
    public enum PushType {
        /**
         * 新消息
         */
        NEW_MESSAGE,
        
        /**
         * 消息已读
         */
        MESSAGE_READ,
        
        /**
         * 消息撤回
         */
        MESSAGE_RECALLED
    }
}
