package com.zhicore.message.domain.model;

import com.zhicore.common.exception.DomainException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 消息聚合根（充血模型）
 * 
 * 设计原则：
 * 1. 私有构造函数 + 工厂方法
 * 2. 领域行为封装业务规则
 * 3. 不变量在构造时和方法内保证
 * 
 * 消息顺序性保证 (CP-MSG-01)：
 * 1. 数据库层面：使用 conversation_id + created_at 索引保证查询顺序
 * 2. 消息队列层面：使用 RocketMQ 顺序消息，以 conversationId 作为 shardingKey
 * 3. 推送层面：WebSocket 推送按消息创建时间排序
 *
 * @author ZhiCore Team
 */
@Getter
public class Message {

    /**
     * 消息ID（雪花ID）
     */
    private final Long id;

    /**
     * 会话ID
     */
    private final Long conversationId;

    /**
     * 发送者ID
     */
    private final Long senderId;

    /**
     * 接收者ID
     */
    private final Long receiverId;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 消息内容（文本消息为内容，文件消息为文件名）
     */
    private String content;

    /**
     * 媒体URL（图片或文件的URL）
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
     * 消息内容最大长度
     */
    private static final int MAX_CONTENT_LENGTH = 5000;

    /**
     * 消息可撤回时间（分钟）
     */
    private static final int RECALL_TIME_LIMIT_MINUTES = 2;

    /**
     * 私有构造函数
     */
    private Message(Long id, Long conversationId, Long senderId, 
                    Long receiverId, MessageType type, String content) {
        Assert.notNull(id, "消息ID不能为空");
        Assert.isTrue(id > 0, "消息ID必须为正数");
        Assert.notNull(conversationId, "会话ID不能为空");
        Assert.isTrue(conversationId > 0, "会话ID必须为正数");
        Assert.notNull(senderId, "发送者ID不能为空");
        Assert.isTrue(senderId > 0, "发送者ID必须为正数");
        Assert.notNull(receiverId, "接收者ID不能为空");
        Assert.isTrue(receiverId > 0, "接收者ID必须为正数");
        Assert.notNull(type, "消息类型不能为空");

        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.type = type;
        this.content = content;
        this.isRead = false;
        this.status = MessageStatus.SENT;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 私有构造函数（用于从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Message(@JsonProperty("id") Long id,
                    @JsonProperty("conversationId") Long conversationId,
                    @JsonProperty("senderId") Long senderId,
                    @JsonProperty("receiverId") Long receiverId,
                    @JsonProperty("type") MessageType type,
                    @JsonProperty("content") String content,
                    @JsonProperty("mediaUrl") String mediaUrl,
                    @JsonProperty("isRead") boolean isRead,
                    @JsonProperty("readAt") LocalDateTime readAt,
                    @JsonProperty("status") MessageStatus status,
                    @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.type = type;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.isRead = isRead;
        this.readAt = readAt;
        this.status = status;
        this.createdAt = createdAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建文本消息
     *
     * @param id 消息ID
     * @param conversationId 会话ID
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param content 消息内容
     * @return 消息实例
     */
    public static Message createText(Long id, Long conversationId,
                                     Long senderId, Long receiverId, String content) {
        validateTextContent(content);
        return new Message(id, conversationId, senderId, receiverId, MessageType.TEXT, content);
    }

    /**
     * 创建图片消息
     *
     * @param id 消息ID
     * @param conversationId 会话ID
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param imageUrl 图片URL
     * @return 消息实例
     */
    public static Message createImage(Long id, Long conversationId,
                                      Long senderId, Long receiverId, String imageUrl) {
        Assert.hasText(imageUrl, "图片URL不能为空");
        Message message = new Message(id, conversationId, senderId, receiverId, MessageType.IMAGE, null);
        message.mediaUrl = imageUrl;
        return message;
    }

    /**
     * 创建文件消息
     *
     * @param id 消息ID
     * @param conversationId 会话ID
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param fileName 文件名
     * @param fileUrl 文件URL
     * @return 消息实例
     */
    public static Message createFile(Long id, Long conversationId,
                                     Long senderId, Long receiverId,
                                     String fileName, String fileUrl) {
        Assert.hasText(fileUrl, "文件URL不能为空");
        Message message = new Message(id, conversationId, senderId, receiverId, MessageType.FILE, fileName);
        message.mediaUrl = fileUrl;
        return message;
    }

    /**
     * 从持久化恢复消息
     *
     * @return 消息实例
     */
    public static Message reconstitute(Long id, Long conversationId, Long senderId, Long receiverId,
                                       MessageType type, String content, String mediaUrl,
                                       boolean isRead, LocalDateTime readAt, MessageStatus status,
                                       LocalDateTime createdAt) {
        return new Message(id, conversationId, senderId, receiverId, type, content, mediaUrl,
                isRead, readAt, status, createdAt);
    }

    // ==================== 领域行为 ====================

    /**
     * 标记消息为已读
     */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }

    /**
     * 撤回消息
     *
     * @param operatorId 操作者ID
     */
    public void recall(Long operatorId) {
        if (!this.senderId.equals(operatorId)) {
            throw new DomainException("只能撤回自己发送的消息");
        }
        if (this.status == MessageStatus.RECALLED) {
            throw new DomainException("消息已经撤回");
        }
        // 检查是否在可撤回时间内
        if (this.createdAt.plusMinutes(RECALL_TIME_LIMIT_MINUTES).isBefore(LocalDateTime.now())) {
            throw new DomainException("消息发送超过" + RECALL_TIME_LIMIT_MINUTES + "分钟，无法撤回");
        }

        this.status = MessageStatus.RECALLED;
        this.content = "[消息已撤回]";
        this.mediaUrl = null;
    }

    /**
     * 检查消息是否已撤回
     *
     * @return 是否已撤回
     */
    public boolean isRecalled() {
        return this.status == MessageStatus.RECALLED;
    }

    /**
     * 获取消息预览内容（用于会话列表显示）
     *
     * @param maxLength 最大长度
     * @return 预览内容
     */
    public String getPreviewContent(int maxLength) {
        if (this.status == MessageStatus.RECALLED) {
            return "[消息已撤回]";
        }
        
        return switch (this.type) {
            case TEXT -> truncateContent(this.content, maxLength);
            case IMAGE -> "[图片]";
            case FILE -> "[文件] " + (this.content != null ? this.content : "");
        };
    }

    // ==================== 私有方法 ====================

    /**
     * 验证文本内容
     */
    private static void validateTextContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new DomainException("消息内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new DomainException("消息内容不能超过" + MAX_CONTENT_LENGTH + "字");
        }
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
