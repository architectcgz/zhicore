package com.blog.message.domain.model;

/**
 * 消息状态枚举
 *
 * @author Blog Team
 */
public enum MessageStatus {
    
    /**
     * 已发送
     */
    SENT(0, "已发送"),
    
    /**
     * 已撤回
     */
    RECALLED(1, "已撤回");
    
    private final int code;
    private final String description;
    
    MessageStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static MessageStatus fromCode(int code) {
        for (MessageStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown message status code: " + code);
    }
}
