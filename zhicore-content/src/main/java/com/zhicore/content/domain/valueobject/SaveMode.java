package com.zhicore.content.domain.valueobject;

import lombok.Getter;

/**
 * 草稿保存模式
 * 表示草稿是自动保存还是手动保存
 * 
 * @author ZhiCore Team
 */
@Getter
public enum SaveMode {
    /**
     * 自动保存 - 编辑器定时触发
     */
    AUTO("自动保存"),
    
    /**
     * 手动保存 - 用户主动触发
     */
    MANUAL("手动保存");
    
    private final String description;
    
    SaveMode(String description) {
        this.description = description;
    }
}
