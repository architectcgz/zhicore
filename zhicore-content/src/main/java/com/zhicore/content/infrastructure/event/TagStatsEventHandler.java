package com.zhicore.content.infrastructure.event;

import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.event.PostPurgedEvent;
import com.zhicore.content.domain.event.PostRestoredEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.application.port.store.TagStatsCacheStore;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.infrastructure.persistence.pg.mapper.TagStatsEntityMyBatisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tag Statistics Event Handler。
 *
 * 基于可重放任务更新 tag_stats 表和 Redis 缓存。
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagStatsEventHandler {

    private final TagStatsEntityMyBatisMapper tagStatsMapper;
    private final TagStatsCacheStore tagStatsCacheStore;

    /**
     * 处理文章创建事件
     * 
     * 当文章创建时，更新文章关联的所有标签的统计数据
     * 
     * @param event 文章创建事件
     */
    public void handlePostCreated(PostCreatedDomainEvent event) {
        log.info("Handling PostCreatedEvent for tag stats: postId={}, tagIds={}",
                event.getPostId(), event.getTagIds());

        if (event.getTagIds() == null || event.getTagIds().isEmpty()) {
            log.debug("No tags to update stats for postId={}", event.getPostId());
            return;
        }

        List<Long> tagIdList = convertTagIds(event.getTagIds());
        updateTagStats(tagIdList);
        invalidateTagStatsCache(tagIdList);
    }

    /**
     * 处理文章删除事件。
     */
    public void handlePostDeleted(PostDeletedEvent event) {
        refreshTagStats(event.getPostId().toString(), event.getTagIds());
    }

    /**
     * 处理文章标签更新事件。
     */
    public void handlePostTagsUpdated(PostTagsUpdatedDomainEvent event) {
        log.info("Handling PostTagsUpdatedEvent for tag stats: postId={}, oldTagIds={}, newTagIds={}",
                event.getPostId(), event.getOldTagIds(), event.getNewTagIds());

        Set<Long> affectedTagIds = new HashSet<>();
        if (event.getOldTagIds() != null) {
            affectedTagIds.addAll(convertTagIds(event.getOldTagIds()));
        }
        if (event.getNewTagIds() != null) {
            affectedTagIds.addAll(convertTagIds(event.getNewTagIds()));
        }

        if (affectedTagIds.isEmpty()) {
            log.debug("No tags to update stats for postId={}", event.getPostId());
            return;
        }

        List<Long> tagIdList = new ArrayList<>(affectedTagIds);
        updateTagStats(tagIdList);
        invalidateTagStatsCache(tagIdList);
    }

    public void handlePostRestored(PostRestoredEvent event) {
        refreshTagStats(event.getPostId().toString(), event.getTagIds());
    }

    public void handlePostPurged(PostPurgedEvent event) {
        refreshTagStats(event.getPostId().toString(), event.getTagIds());
    }

    /**
     * 更新标签统计
     * 
     * 批量更新 tag_stats 表，基于 post_tags 表实时计算
     * 
     * @param tagIds 标签ID列表
     */
    private void updateTagStats(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        log.debug("Updating tag stats for {} tags", tagIds.size());
        tagStatsMapper.batchUpsertTagStats(tagIds);
        log.debug("Successfully updated tag stats for {} tags", tagIds.size());
    }

    /**
     * 清除标签统计相关缓存
     * 
     * 清除：
     * 1. tag:stats:{tagId} - 单个标签统计缓存
     * 2. tags:hot - 热门标签缓存
     * 
     * @param tagIds 标签ID列表
     */
    private void invalidateTagStatsCache(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        tagStatsCacheStore.evictTagStats(tagIds);
        tagStatsCacheStore.evictHotTags();
    }

    /**
     * 清除热门标签缓存
     * 
     * 清除所有 tags:hot:* 缓存键
     */
    private void invalidateHotTagsCache() {
        tagStatsCacheStore.evictHotTags();
    }

    /**
     * 转换 TagId 集合为 Long 列表
     * 
     * @param tagIds TagId 集合
     * @return Long ID列表
     */
    private List<Long> convertTagIds(java.util.Set<TagId> tagIds) {
        return tagIds.stream()
                .map(TagId::getValue)
                .collect(java.util.stream.Collectors.toList());
    }

    private void refreshTagStats(String postId, Set<TagId> tagIds) {
        log.info("Refreshing tag stats for postId={}, tagIds={}", postId, tagIds);
        if (tagIds == null || tagIds.isEmpty()) {
            invalidateHotTagsCache();
            return;
        }

        List<Long> tagIdList = convertTagIds(tagIds);
        updateTagStats(tagIdList);
        invalidateTagStatsCache(tagIdList);
    }
}
