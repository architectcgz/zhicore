package com.blog.post.infrastructure.mongodb.repository;

import com.blog.post.infrastructure.mongodb.document.PostContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文章内容 Repository（MongoDB）
 * 管理文章内容的存储和查询
 *
 * @author Blog Team
 */
@Repository
public interface PostContentRepository extends MongoRepository<PostContent, String> {

    /**
     * 根据文章ID查询内容
     *
     * @param postId 文章ID
     * @return 文章内容
     */
    Optional<PostContent> findByPostId(String postId);

    /**
     * 删除指定文章的内容
     *
     * @param postId 文章ID
     */
    void deleteByPostId(String postId);

    /**
     * 批量查询内容
     *
     * @param postIds 文章ID列表
     * @return 文章内容列表
     */
    List<PostContent> findByPostIdIn(List<String> postIds);

    /**
     * 检查文章内容是否存在
     *
     * @param postId 文章ID
     * @return 是否存在
     */
    boolean existsByPostId(String postId);
}
