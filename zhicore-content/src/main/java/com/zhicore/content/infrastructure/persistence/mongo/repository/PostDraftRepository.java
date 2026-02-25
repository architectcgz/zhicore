package com.zhicore.content.infrastructure.persistence.mongo.repository;

import com.zhicore.content.infrastructure.persistence.mongo.document.PostDraft;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章草稿 Repository（MongoDB）
 * 管理文章草稿的存储和查询，支持自动保存功能
 *
 * @author ZhiCore Team
 */
@Repository
public interface PostDraftRepository extends MongoRepository<PostDraft, String> {

    /**
     * 根据文章ID和用户ID查询草稿
     * 每个用户每篇文章只有一份草稿（Upsert模式）
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 草稿内容
     */
    Optional<PostDraft> findByPostIdAndUserId(String postId, String userId);

    /**
     * 查询用户的所有草稿
     * 按保存时间倒序排列
     *
     * @param userId 用户ID
     * @return 草稿列表
     */
    List<PostDraft> findByUserIdOrderBySavedAtDesc(String userId);

    /**
     * 删除指定文章和用户的草稿
     *
     * @param postId 文章ID
     * @param userId 用户ID
     */
    void deleteByPostIdAndUserId(String postId, String userId);

    /**
     * 删除过期草稿
     * 删除保存时间早于指定时间的草稿
     *
     * @param expireTime 过期时间
     * @return 删除的数量
     */
    long deleteBySavedAtBefore(LocalDateTime expireTime);

    /**
     * 检查草稿是否存在
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 是否存在
     */
    boolean existsByPostIdAndUserId(String postId, String userId);

    /**
     * 统计指定文章和用户的草稿数量
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 草稿数量
     */
    long countByPostIdAndUserId(String postId, String userId);
}
