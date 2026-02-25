package com.zhicore.content.domain.service;

import com.zhicore.content.domain.valueobject.DraftSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 草稿管理器接口
 * 管理文章草稿的自动保存和恢复，支持编辑器自动保存功能
 *
 * @author ZhiCore Team
 */
public interface DraftService {

    /**
     * 保存草稿（Upsert模式）
     * 每个用户每篇文章只保留一份草稿，新保存会覆盖旧草稿
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @param content 草稿内容
     * @param isAutoSave 是否自动保存
     */
    void saveDraft(Long postId, Long userId, String content, boolean isAutoSave);

    /**
     * 获取最新草稿
     * 查询指定文章和用户的最新草稿
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 草稿内容
     */
    Optional<DraftSnapshot> getLatestDraft(Long postId, Long userId);

    /**
     * 获取用户所有草稿
     * 按保存时间倒序返回用户的所有草稿
     *
     * @param userId 用户ID
     * @return 草稿列表
     */
    List<DraftSnapshot> getUserDrafts(Long userId);

    /**
     * 删除草稿
     * 当用户发布文章或主动删除草稿时调用
     *
     * @param postId 文章ID
     * @param userId 用户ID
     */
    void deleteDraft(Long postId, Long userId);

    /**
     * 清理过期草稿
     * 删除超过指定天数未更新的草稿
     *
     * @param expireDays 过期天数
     * @return 清理的草稿数量
     */
    long cleanExpiredDrafts(int expireDays);
}
