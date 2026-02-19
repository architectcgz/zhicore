package com.blog.post.infrastructure.mongodb.repository;

import com.blog.post.infrastructure.mongodb.document.PostVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文章版本 Repository（MongoDB）
 * 管理文章版本历史的存储和查询
 *
 * @author Blog Team
 */
@Repository
public interface PostVersionRepository extends MongoRepository<PostVersion, String> {

    /**
     * 根据文章ID和版本号查询版本
     *
     * @param postId 文章ID
     * @param version 版本号
     * @return 版本详情
     */
    Optional<PostVersion> findByPostIdAndVersion(String postId, Integer version);

    /**
     * 根据文章ID分页查询版本列表（按编辑时间倒序）
     *
     * @param postId 文章ID
     * @param pageable 分页参数
     * @return 版本列表
     */
    Page<PostVersion> findByPostIdOrderByEditedAtDesc(String postId, Pageable pageable);

    /**
     * 查询文章的最大版本号
     *
     * @param postId 文章ID
     * @return 最大版本号
     */
    @Query(value = "{ 'postId': ?0 }", fields = "{ 'version': 1 }", sort = "{ 'version': -1 }")
    Optional<PostVersion> findTopByPostIdOrderByVersionDesc(String postId);

    /**
     * 统计文章的版本数量
     *
     * @param postId 文章ID
     * @return 版本数量
     */
    long countByPostId(String postId);

    /**
     * 查询文章的所有版本（按版本号倒序）
     *
     * @param postId 文章ID
     * @return 版本列表
     */
    List<PostVersion> findByPostIdOrderByVersionDesc(String postId);

    /**
     * 删除指定文章的所有版本
     *
     * @param postId 文章ID
     */
    void deleteByPostId(String postId);
}
