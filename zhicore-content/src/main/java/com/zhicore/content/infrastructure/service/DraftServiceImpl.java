package com.zhicore.content.infrastructure.service;

import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.service.DraftService;
import com.zhicore.content.domain.valueobject.DraftSnapshot;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostDraft;
import com.zhicore.content.infrastructure.persistence.mongo.repository.PostDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 草稿管理器实现
 * 实现文章草稿的自动保存、查询、删除和清理功能
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftServiceImpl implements DraftService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final PostDraftRepository draftRepository;

    /**
     * 保存草稿（Upsert模式）
     * 每个用户每篇文章只保留一份草稿，新保存会覆盖旧草稿
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @param content 草稿内容
     * @param isAutoSave 是否自动保存
     */
    @Override
    @Transactional
    public void saveDraft(Long postId, Long userId, String content, boolean isAutoSave) {
        log.debug("Saving draft for postId={}, userId={}, isAutoSave={}", postId, userId, isAutoSave);
        
        try {
            // 查询是否存在草稿
            Optional<PostDraft> existingDraft = draftRepository.findByPostIdAndUserId(String.valueOf(postId), String.valueOf(userId));
            
            PostDraft draft;
            if (existingDraft.isPresent()) {
                // 更新现有草稿
                draft = existingDraft.get();
                draft.setContent(content);
                draft.setSavedAt(LocalDateTime.now());
                draft.setIsAutoSave(isAutoSave);
                draft.setWordCount(calculateWordCount(content));
            } else {
                // 创建新草稿
                draft = PostDraft.builder()
                        .postId(String.valueOf(postId))
                        .userId(String.valueOf(userId))
                        .content(content)
                        .contentType(ContentType.MARKDOWN.getValue()) // 默认为markdown
                        .savedAt(LocalDateTime.now())
                        .isAutoSave(isAutoSave)
                        .wordCount(calculateWordCount(content))
                        .build();
            }
            
            draftRepository.save(draft);
            log.info("Draft saved successfully for postId={}, userId={}", postId, userId);
            
        } catch (Exception e) {
            log.error("Failed to save draft for postId={}, userId={}", postId, userId, e);
            // 草稿保存失败不应该中断用户编辑流程，只记录错误
            // 根据需求 4.5，草稿保存失败应该在前端显示警告但不中断编辑
        }
    }

    /**
     * 获取最新草稿
     * 查询指定文章和用户的最新草稿
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 草稿内容
     */
    @Override
    public Optional<DraftSnapshot> getLatestDraft(Long postId, Long userId) {
        log.debug("Getting latest draft for postId={}, userId={}", postId, userId);
        
        try {
            Optional<PostDraft> draft = draftRepository.findByPostIdAndUserId(String.valueOf(postId), String.valueOf(userId));
            
            if (draft.isPresent()) {
                log.info("Found draft for postId={}, userId={}, savedAt={}", 
                        postId, userId, draft.get().getSavedAt());
            } else {
                log.debug("No draft found for postId={}, userId={}", postId, userId);
            }
            
            return draft.map(this::toSnapshot);
            
        } catch (Exception e) {
            log.error("Failed to get draft for postId={}, userId={}", postId, userId, e);
            return Optional.empty();
        }
    }

    /**
     * 获取用户所有草稿
     * 按保存时间倒序返回用户的所有草稿
     *
     * @param userId 用户ID
     * @return 草稿列表
     */
    @Override
    public List<DraftSnapshot> getUserDrafts(Long userId) {
        log.debug("Getting all drafts for userId={}", userId);
        
        try {
            List<PostDraft> drafts = draftRepository.findByUserIdOrderBySavedAtDesc(String.valueOf(userId));
            log.info("Found {} drafts for userId={}", drafts.size(), userId);
            return drafts.stream().map(this::toSnapshot).toList();
            
        } catch (Exception e) {
            log.error("Failed to get drafts for userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 删除草稿
     * 当用户发布文章或主动删除草稿时调用
     *
     * @param postId 文章ID
     * @param userId 用户ID
     */
    @Override
    @Transactional
    public void deleteDraft(Long postId, Long userId) {
        log.debug("Deleting draft for postId={}, userId={}", postId, userId);
        
        try {
            draftRepository.deleteByPostIdAndUserId(String.valueOf(postId), String.valueOf(userId));
            log.info("Draft deleted successfully for postId={}, userId={}", postId, userId);
            
        } catch (Exception e) {
            log.error("Failed to delete draft for postId={}, userId={}", postId, userId, e);
            // 删除失败不应该影响主流程，只记录错误
        }
    }

    /**
     * 清理过期草稿
     * 删除超过指定天数未更新的草稿
     *
     * @param expireDays 过期天数
     * @return 清理的草稿数量
     */
    @Override
    @Transactional
    public long cleanExpiredDrafts(int expireDays) {
        log.info("Cleaning expired drafts older than {} days", expireDays);
        
        try {
            LocalDateTime expireTime = LocalDateTime.now().minusDays(expireDays);
            long deletedCount = draftRepository.deleteBySavedAtBefore(expireTime);
            
            log.info("Cleaned {} expired drafts", deletedCount);
            return deletedCount;
            
        } catch (Exception e) {
            log.error("Failed to clean expired drafts", e);
            return 0;
        }
    }

    /**
     * 计算字数
     * 简单实现：去除空白字符后的字符数
     *
     * @param content 内容
     * @return 字数
     */
    private int calculateWordCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        
        // 移除空白字符并计算字符数
        String trimmed = WHITESPACE.matcher(content).replaceAll("");
        return trimmed.length();
    }

    private DraftSnapshot toSnapshot(PostDraft draft) {
        return DraftSnapshot.builder()
                .id(draft.getId())
                .postId(draft.getPostId())
                .userId(draft.getUserId())
                .content(draft.getContent())
                .contentType(draft.getContentType())
                .savedAt(draft.getSavedAt())
                .deviceId(draft.getDeviceId())
                .isAutoSave(draft.getIsAutoSave())
                .wordCount(draft.getWordCount())
                .build();
    }
}

