package com.zhicore.message.domain.model;

/**
 * 消息类型枚举
 *
 * @author ZhiCore Team
 */
public enum MessageType {
    
    /**
     * 文本消息
     */
    TEXT(0, "文本"),
    
    /**
     * 图片消息
     */
    IMAGE(1, "图片"),
    
    /**
     * 文件消息
     */
    FILE(2, "文件");
    
    private final int code;
    private final String description;
    
    MessageType(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
