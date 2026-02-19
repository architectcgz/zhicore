package com.blog.post.infrastructure.mongodb.repository;

import com.blog.post.infrastructure.mongodb.document.PostDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文章文档仓储接口（MongoDB）
 *
 * @author Blog Team
 */
@Repository
public interface PostDocumentRepository extends MongoRepository<PostDocument, String> {

    /**
     * 根据文章ID查询
     *
     * @param postId 文章ID
     * @return 文章文档
     */
    Optional<PostDocument> findByPostId(String postId);

    /**
     * 根据文章ID删除
     *
     * @param postId 文章ID
     */
    void deleteByPostId(String postId);

    /**
     * 检查文章是否存在
     *
     * @param postId 文章ID
     * @return 是否存在
     */
    boolean existsByPostId(String postId);
}
