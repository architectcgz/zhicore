package com.zhicore.content.infrastructure.mongodb.repository;

import com.zhicore.content.infrastructure.mongodb.document.PostArchive;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文章归档 Repository（MongoDB）
 * 管理归档文章的存储和查询
 *
 * @author ZhiCore Team
 */
@Repository
public interface PostArchiveRepository extends MongoRepository<PostArchive, String> {

    /**
     * 根据文章ID查询归档内容
     *
     * @param postId 文章ID
     * @return 归档内容
     */
    Optional<PostArchive> findByPostId(String postId);

    /**
     * 删除指定文章的归档
     *
     * @param postId 文章ID
     */
    void deleteByPostId(String postId);

    /**
     * 检查文章是否已归档
     *
     * @param postId 文章ID
     * @return 是否已归档
     */
    boolean existsByPostId(String postId);
}
