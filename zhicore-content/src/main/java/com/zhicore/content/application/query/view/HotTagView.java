package com.zhicore.content.application.query.view;

import lombok.Builder;
import lombok.Data;

/**
 * 热门标签视图
 * 
 * 包含标签信息和文章数量
 * 
 * @author ZhiCore Team
 */
@Data
@Builder
public class HotTagView {
    
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
     * 文章数量
     */
    private Long postCount;
}
