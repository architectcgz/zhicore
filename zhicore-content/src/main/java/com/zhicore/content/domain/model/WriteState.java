package com.zhicore.content.domain.model;

import lombok.Getter;

/**
 * 写入状态枚举
 * 
 * 用于跟踪三阶段写入流程的进度，支持补偿机制。
 * 
 * @author ZhiCore Team
 */
@Getter
public enum WriteState {
    
    /**
     * 未开始
     */
    NONE(0, "未开始"),
    
    /**
     * 只有 PostgreSQL 草稿
     */
    DRAFT_ONLY(1, "草稿已创建"),
    
    /**
     * PostgreSQL + MongoDB 都有数据
     */
    CONTENT_SAVED(2, "内容已保存"),
    
    /**
     * 已发布（完整状态）
     */
    PUBLISHED(3, "已发布"),
    
    /**
     * 不完整状态（需要清理）
     */
    INCOMPLETE(4, "不完整");
    
    private final int code;
    private final String description;
    
    WriteState(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public static WriteState fromCode(int code) {
        for (WriteState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown WriteState code: " + code);
    }
}
