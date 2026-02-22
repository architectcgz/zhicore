package com.zhicore.content.infrastructure.event;

import com.zhicore.api.event.post.PostDeletedEvent;
import com.zhicore.content.domain.event.PostCreatedEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedEvent;
import com.zhicore.content.infrastructure.cache.TagRedisKeys;
import com.zhicore.content.infrastructure.repository.mapper.TagStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tag Statistics Event Handler
 * 
 * 监听文章相关事件，异步更新 tag_stats 表和 Redis 缓存
 * 
 * 处理的事件：
 * - PostCreatedEvent: 文章创建时更新相关标签的统计
 * - PostDeletedEvent: 文章删除时更新相关标签的统计
 * - PostTagsUpdatedEvent: 文章标签更新时更新相关标签的统计
 * 
 * Requirements: 4.4
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagStatsEventHandler {

    private final TagStatsMapper tagStatsMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理文章创建事件
     * 
     * 当文章创建时，更新文章关联的所有标签的统计数据
     * 
     * @param event 文章创建事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostCreated(PostCreatedEvent event) {
        try {
            log.info("Handling PostCreatedEvent for tag stats: postId={}, tagIds={}", 
                    event.getPostId(), event.getTagIds());

            List<String> tagIds = event.getTagIds();
            if (tagIds == null || tagIds.isEmpty()) {
                log.debug("No tags to update stats for postId={}", event.getPostId());
                return;
            }

            // 转换为 Long 类型
            List<Long> tagIdList = convertToLongList(tagIds);

            // 批量更新 tag_stats 表
            updateTagStats(tagIdList);

            // 清除相关缓存
            invalidateTagStatsCache(tagIdList);

            log.info("Successfully updated tag stats for PostCreatedEvent: postId={}, tagCount={}", 
                    event.getPostId(), tagIds.size());

        } catch (Exception e) {
            log.error("Failed to handle PostCreatedEvent for tag stats: postId={}", 
                    event.getPostId(), e);
        }
    }

    /**
     * 处理文章删除事件
     * 
     * 当文章删除时，需要先查询文章关联的标签，然后更新这些标签的统计数据
     * 
     * 注意：由于 PostDeletedEvent 不包含标签信息，这里采用简化策略：
     * 依赖数据库的 ON DELETE CASCADE 自动删除 post_tags 记录
     * 然后通过定时任务或手动触发重新计算所有标签的统计
     * 
     * @param event 文章删除事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostDeleted(PostDeletedEvent event) {
        try {
            log.info("Handling PostDeletedEvent for tag stats: postId={}", event.getPostId());

            // 由于 PostDeletedEvent 不包含标签信息，且 post_tags 记录已被级联删除
            // 这里清除热门标签缓存，让下次查询时重新计算
            invalidateHotTagsCache();

            log.info("Successfully handled PostDeletedEvent for tag stats: postId={}", 
                    event.getPostId());

        } catch (Exception e) {
            log.error("Failed to handle PostDeletedEvent for tag stats: postId={}", 
                    event.getPostId(), e);
        }
    }

    /**
     * 处理文章标签更新事件
     * 
     * 当文章标签更新时，需要更新旧标签和新标签的统计数据
     * 
     * @param event 文章标签更新事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostTagsUpdated(PostTagsUpdatedEvent event) {
        try {
            log.info("Handling PostTagsUpdatedEvent for tag stats: postId={}, oldTagIds={}, newTagIds={}", 
                    event.getPostId(), event.getOldTagIds(), event.getNewTagIds());

            // 收集所有受影响的标签ID（旧标签 + 新标签）
            Set<Long> affectedTagIds = new HashSet<>();
            
            if (event.getOldTagIds() != null) {
                affectedTagIds.addAll(convertToLongList(event.getOldTagIds()));
            }
            
            if (event.getNewTagIds() != null) {
                affectedTagIds.addAll(convertToLongList(event.getNewTagIds()));
            }

            if (affectedTagIds.isEmpty()) {
                log.debug("No tags to update stats for postId={}", event.getPostId());
                return;
            }

            List<Long> tagIdList = new ArrayList<>(affectedTagIds);

            // 批量更新 tag_stats 表
            updateTagStats(tagIdList);

            // 清除相关缓存
            invalidateTagStatsCache(tagIdList);

            log.info("Successfully updated tag stats for PostTagsUpdatedEvent: postId={}, affectedTagCount={}", 
                    event.getPostId(), affectedTagIds.size());

        } catch (Exception e) {
            log.error("Failed to handle PostTagsUpdatedEvent for tag stats: postId={}", 
                    event.getPostId(), e);
        }
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

        try {
            log.debug("Updating tag stats for {} tags", tagIds.size());
            tagStatsMapper.batchUpsertTagStats(tagIds);
            log.debug("Successfully updated tag stats for {} tags", tagIds.size());
        } catch (Exception e) {
            log.error("Failed to update tag stats for tagIds={}", tagIds, e);
            throw e;
        }
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

        try {
            // 1. 清除单个标签统计缓存
            for (Long tagId : tagIds) {
                String statsKey = TagRedisKeys.tagStats(tagId);
                redisTemplate.delete(statsKey);
                log.debug("Invalidated tag stats cache: key={}", statsKey);
            }

            // 2. 清除热门标签缓存
            invalidateHotTagsCache();

        } catch (Exception e) {
            log.error("Failed to invalidate tag stats cache for tagIds={}", tagIds, e);
            // 缓存失效失败不影响主流程，只记录日志
        }
    }

    /**
     * 清除热门标签缓存
     * 
     * 清除所有 tags:hot:* 缓存键
     */
    private void invalidateHotTagsCache() {
        try {
            // 清除所有热门标签缓存（包含不同 limit 参数的缓存）
            Set<String> keys = redisTemplate.keys(TagRedisKeys.HOT_TAGS_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} hot tags cache keys", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to invalidate hot tags cache", e);
            // 缓存失效失败不影响主流程，只记录日志
        }
    }

    /**
     * 转换字符串ID列表为Long列表
     * 
     * @param stringIds 字符串ID列表
     * @return Long ID列表
     */
    private List<Long> convertToLongList(List<String> stringIds) {
        List<Long> longIds = new ArrayList<>();
        for (String id : stringIds) {
            try {
                longIds.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse tag ID: {}", id);
            }
        }
        return longIds;
    }
}
