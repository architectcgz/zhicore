package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 内容类型枚举
 * 
 * 定义文章支持的所有内容格式类型。
 * 使用枚举提供类型安全，避免硬编码字符串。
 *
 * @author ZhiCore Team
 */
@Getter
public enum ContentType {
    
    /**
     * Markdown 格式
     */
    MARKDOWN("markdown"),
    
    /**
     * HTML 格式
     */
    HTML("html"),
    
    /**
     * 富文本格式
     */
    RICH("rich");
    
    /**
     * 字符串值（用于数据库存储和 API 传输）
     */
    private final String value;
    
    ContentType(String value) {
        this.value = value;
    }
    
    /**
     * 获取字符串值
     * 
     * @JsonValue 注解用于 Jackson 序列化时使用此方法
     * @return 字符串值
     */
    @JsonValue
    public String getValue() {
        return value;
    }
    
    /**
     * 从字符串值解析枚举
     * 
     * @JsonCreator 注解用于 Jackson 反序列化时使用此方法
     * @param value 字符串值
     * @return ContentType 枚举
     * @throws IllegalArgumentException 如果值无效
     */
    @JsonCreator
    public static ContentType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return MARKDOWN; // 默认值
        }
        
        String normalizedValue = value.trim().toLowerCase();
        for (ContentType type : ContentType.values()) {
            if (type.value.equals(normalizedValue)) {
                return type;
            }
        }
        
        // 未知类型抛出异常
        throw new IllegalArgumentException(
            String.format("无效的内容类型: %s, 有效值: markdown, html, rich", value)
        );
    }
    
    /**
     * 从字符串值解析枚举（带默认值）
     * 
     * 用于持久化层，遇到无效值时返回默认值而不抛出异常
     * 
     * @param value 字符串值
     * @param defaultValue 默认值
     * @return ContentType 枚举
     */
    public static ContentType fromValueOrDefault(String value, ContentType defaultValue) {
        try {
            return fromValue(value);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
