package com.zhicore.content.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

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
    void attach(Long postId, Long tagId);

    /**
     * 批量创建关联
     * 
     * @param postId 文章ID
     * @param tagIds 标签ID列表
     */
    void attachBatch(Long postId, List<Long> tagIds);

    /**
     * 删除关联
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     */
    void detach(Long postId, Long tagId);

    /**
     * 删除文章的所有关联
     * 
     * @param postId 文章ID
     */
    void detachAllByPostId(Long postId);

    /**
     * 查询文章的所有标签ID
     * 
     * @param postId 文章ID
     * @return 标签ID列表
     */
    List<Long> findTagIdsByPostId(Long postId);

    /**
     * 查询标签下的所有文章ID
     * 
     * @param tagId 标签ID
     * @return 文章ID列表
     */
    List<Long> findPostIdsByTagId(Long tagId);

    /**
     * 分页查询标签下的文章ID
     * 
     * @param tagId 标签ID
     * @param pageable 分页参数
     * @return 文章ID分页结果
     */
    Page<Long> findPostIdsByTagId(Long tagId, Pageable pageable);

    /**
     * 检查关联是否存在
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     * @return 是否存在
     */
    boolean exists(Long postId, Long tagId);

    /**
     * 统计标签下的文章数量
     * 
     * @param tagId 标签ID
     * @return 文章数量
     */
    int countPostsByTagId(Long tagId);

    /**
     * 统计文章的标签数量
     * 
     * @param postId 文章ID
     * @return 标签数量
     */
    int countTagsByPostId(Long postId);

    /**
     * 批量查询文章的标签（避免 N+1 查询）
     * 
     * 用于文章列表场景，一次性加载多篇文章的标签信息
     * 
     * @param postIds 文章ID列表
     * @return Map<文章ID, 标签ID列表>
     */
    java.util.Map<Long, List<Long>> findTagIdsByPostIds(List<Long> postIds);
}
