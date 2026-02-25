package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 话题 ID 值对象
 * 
 * 封装话题 ID，提供类型安全和领域语义。
 * 
 * @author ZhiCore Team
 */
@Getter
@EqualsAndHashCode
@ToString
public class TopicId {
    
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
    public TopicId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("TopicId 值必须为正数");
        }
        this.value = value;
    }

    /**
     * 从字符串创建 TopicId（兼容旧调用）
     */
    public TopicId(String value) {
        this(Long.parseLong(value));
    }
    
    /**
     * 从 Long 创建 TopicId
     * 
     * @param value ID 值
     * @return TopicId 实例
     */
    public static TopicId of(Long value) {
        return new TopicId(value);
    }
}
