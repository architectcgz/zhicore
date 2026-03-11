package com.zhicore.content.domain.service;

/**
 * 草稿写服务接口。
 *
 * 负责草稿保存、删除与清理等写操作。
 */
public interface DraftCommandService {

    /**
     * 保存草稿（Upsert）。
     *
     * @param postId 文章 ID
     * @param userId 用户 ID
     * @param content 草稿内容
     * @param isAutoSave 是否自动保存
     */
    void saveDraft(Long postId, Long userId, String content, boolean isAutoSave);

    /**
     * 删除草稿。
     *
     * @param postId 文章 ID
     * @param userId 用户 ID
     */
    void deleteDraft(Long postId, Long userId);

    /**
     * 清理过期草稿。
     *
     * @param expireDays 过期天数
     * @return 删除数量
     */
    long cleanExpiredDrafts(int expireDays);
}
