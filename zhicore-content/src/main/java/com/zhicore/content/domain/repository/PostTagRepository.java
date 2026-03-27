package com.zhicore.content.domain.repository;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * Post-Tag 关联仓储接口
 * 
 * 管理 Post 与 Tag 的多对多关系
 *
 * @author ZhiCore Team
 */
public interface PostTagRepository {

    /**
     * 创建关联
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     */
    void attach(PostId postId, TagId tagId);

    /**
     * 批量创建关联
     * 
     * @param postId 文章ID
     * @param tagIds 标签ID列表
     */
    void attachBatch(PostId postId, List<TagId> tagIds);

    /**
     * 删除关联
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     */
    void detach(PostId postId, TagId tagId);

    /**
     * 删除文章的所有关联
     * 
     * @param postId 文章ID
     */
    void detachAllByPostId(PostId postId);

    /**
     * 查询文章的所有标签ID
     * 
     * @param postId 文章ID
     * @return 标签ID列表
     */
    List<TagId> findTagIdsByPostId(PostId postId);

    /**
     * 查询标签下的所有文章ID
     * 
     * @param tagId 标签ID
     * @return 文章ID列表
     */
    List<PostId> findPostIdsByTagId(TagId tagId);

    /**
     * 分页查询标签下的文章ID
     * 
     * @param tagId 标签ID
     * @param pageable 分页参数
     * @return 文章ID分页结果
     */
    Page<PostId> findPostIdsByTagId(TagId tagId, Pageable pageable);

    /**
     * 检查关联是否存在
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     * @return 是否存在
     */
    boolean exists(PostId postId, TagId tagId);

    /**
     * 统计标签下的文章数量
     * 
     * @param tagId 标签ID
     * @return 文章数量
     */
    int countPostsByTagId(TagId tagId);

    /**
     * 统计文章的标签数量
     * 
     * @param postId 文章ID
     * @return 标签数量
     */
    int countTagsByPostId(PostId postId);

    /**
     * 批量查询文章的标签（避免 N+1 查询）
     * 
     * 用于文章列表场景，一次性加载多篇文章的标签信息
     * 
     * @param postIds 文章ID列表
     * @return Map<文章ID, 标签ID列表>
     */
    Map<PostId, List<TagId>> findTagIdsByPostIds(List<PostId> postIds);
}
