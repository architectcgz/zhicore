package com.zhicore.content.application.query.view;

import lombok.Builder;
import lombok.Data;

/**
 * 标签列表项视图
 * 
 * 用于标签列表查询，包含基本信息
 * 
 * @author ZhiCore Team
 */
@Data
@Builder
public class TagListItemView {
    
    /**
     * 标签 ID
     */
    private Long id;
    
    /**
     * 标签名称
     */
    private String name;
    
    /**
     * 标签 slug
     */
    private String slug;
    
    /**
     * 标签描述
     */
    private String description;
}
