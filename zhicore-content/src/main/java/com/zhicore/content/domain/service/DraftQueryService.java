package com.zhicore.content.domain.service;

import com.zhicore.content.domain.valueobject.DraftSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 草稿读服务接口。
 *
 * 负责草稿读取能力，不承载写操作。
 */
public interface DraftQueryService {

    /**
     * 获取指定文章的最新草稿。
     *
     * @param postId 文章 ID
     * @param userId 用户 ID
     * @return 草稿快照
     */
    Optional<DraftSnapshot> getLatestDraft(Long postId, Long userId);

    /**
     * 获取用户的全部草稿列表。
     *
     * @param userId 用户 ID
     * @return 草稿列表
     */
    List<DraftSnapshot> getUserDrafts(Long userId);
}
