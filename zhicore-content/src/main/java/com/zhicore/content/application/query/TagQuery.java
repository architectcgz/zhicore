package com.zhicore.content.application.query;

import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import com.zhicore.content.application.query.view.TagListItemView;

import java.util.List;

/**
 * 标签查询服务接口
 * 
 * 定义所有标签查询操作
 * 
 * @author ZhiCore Team
 */
public interface TagQuery {
    
    /**
     * 获取标签详情（通过 ID）
     * 
     * @param tagId 标签 ID
     * @return 标签详情视图
     */
    TagDetailView getDetail(Long tagId);
    
    /**
     * 获取标签详情（通过 slug）
     * 
     * @param slug 标签 slug
     * @return 标签详情视图
     */
    TagDetailView getDetailBySlug(String slug);
    
    /**
     * 获取标签列表
     * 
     * @param limit 限制数量
     * @return 标签列表
     */
    List<TagListItemView> getList(int limit);
    
    /**
     * 根据名称搜索标签
     * 
     * @param keyword 关键词
     * @param limit 限制数量
     * @return 标签列表
     */
    List<TagListItemView> searchByName(String keyword, int limit);
    
    /**
     * 获取热门标签
     * 
     * @param limit 限制数量
     * @return 热门标签列表
     */
    List<HotTagView> getHotTags(int limit);
}
