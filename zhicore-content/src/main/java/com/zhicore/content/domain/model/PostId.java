package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 文章 ID 值对象
 * 
 * 封装文章 ID，提供类型安全和领域语义。
 * 
 * @author ZhiCore Team
 */
@Getter
@EqualsAndHashCode
@ToString
public class PostId {
    
    @JsonValue  // 序列化时只输出 value 字段
    private final Long value;
    
    // 获取原始 Long 值，用于数据库操作、缓存键生成等基础设施层场景
    public Long getValue() {
        return value;
    }
    
    /**
     * 构造函数
     * 
     * @param value ID 值
     * @throws IllegalArgumentException 如果 value 为 null 或非正数
     */
    @JsonCreator  // 支持 Jackson 反序列化
    public PostId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("PostId 值必须为正数");
        }
        this.value = value;
    }

    /**
     * 从字符串创建 PostId（兼容旧调用）
     */
    public PostId(String value) {
        this(Long.parseLong(value));
    }
    
    /**
     * 从 Long 创建 PostId
     * 
     * @param value ID 值
     * @return PostId 实例
     */
    public static PostId of(Long value) {
        return new PostId(value);
    }
}
