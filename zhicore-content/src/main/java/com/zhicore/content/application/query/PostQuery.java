package com.zhicore.content.application.query;

import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.query.view.PostListItemView;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 文章查询服务接口
 * 
 * 定义所有文章查询操作
 * 
 * @author ZhiCore Team
 */
public interface PostQuery {
    
    /**
     * 获取文章详情
     * 
     * 组装元数据、内容和统计信息
     * 
     * @param postId 文章 ID
     * @return 文章详情视图
     */
    PostDetailView getDetail(PostId postId);
    
    /**
     * 获取最新文章列表
     * 
     * @param pageable 分页参数
     * @return 文章列表
     */
    List<PostListItemView> getLatestPosts(Pageable pageable);
    
    /**
     * 获取指定作者的文章列表
     * 
     * @param authorId 作者 ID
     * @param pageable 分页参数
     * @return 文章列表
     */
    List<PostListItemView> getPostsByAuthor(UserId authorId, Pageable pageable);
    
    /**
     * 获取指定标签的文章列表
     * 
     * @param tagId 标签 ID
     * @param pageable 分页参数
     * @return 文章列表
     */
    List<PostListItemView> getPostsByTag(TagId tagId, Pageable pageable);
}
