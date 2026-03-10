package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.assembler.PostViewAssembler;
import com.zhicore.content.application.assembler.TagAssembler;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.dto.TagStatsDTO;
import com.zhicore.content.application.port.store.TagHotTagsStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tag Application Service
 * 
 * 提供标签相关的应用层服务，包括：
 * - 获取标签详情
 * - 获取标签列表
 * - 搜索标签
 * - 获取标签下的文章列表
 * - 获取热门标签
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagApplicationService {

    private static final Random RANDOM = new Random();

    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final PostRepository postRepository;
    private final TagAssembler tagAssembler;
    private final TagHotTagsStore tagHotTagsStore;

    /**
     * 获取标签详情
     * 
     * Requirements: 4.1.4
     *
     * @param slug 标签 slug
     * @return TagDTO
     * @throws ResourceNotFoundException 标签不存在
     */
    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_TAG_DETAIL,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetTagDetailBlocked"
    )
    public TagDTO getTag(String slug) {
        log.debug("Getting tag by slug: {}", slug);
        
        Tag tag = tagRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("标签不存在: " + slug));
        
        return tagAssembler.toDTO(tag);
    }

    /**
     * 获取标签列表（分页）
     * 
     * Requirements: 4.1.4
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.LIST_TAGS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleListTagsBlocked"
    )
    public PageResult<TagDTO> listTags(int page, int size) {
        log.debug("Listing tags: page={}, size={}", page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Tag> tagPage = tagRepository.findAll(pageable);
        
        List<TagDTO> tagDTOs = tagPage.getContent().stream()
                .map(tagAssembler::toDTO)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, tagPage.getTotalElements(), tagDTOs);
    }

    /**
     * 搜索标签（根据名称模糊搜索）
     * 
     * Requirements: 4.1.4
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量限制
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.SEARCH_TAGS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleSearchTagsBlocked"
    )
    public List<TagDTO> searchTags(String keyword, int limit) {
        log.debug("Searching tags: keyword={}, limit={}", keyword, limit);
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Tag> tags = tagRepository.searchByName(keyword.trim(), limit);
        
        return tags.stream()
                .map(tagAssembler::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取标签下的文章列表（分页）
     * 
     * 实现策略：
     * 1. 根据 slug 查询 Tag
     * 2. 分页查询 Tag 下的 Post ID 列表
     * 3. 批量查询 Post 详情（避免 N+1 问题）
     * 4. 组装 PostDTO 列表
     * 
     * Requirements: 4.3.1, 4.3.2
     *
     * @param slug 标签 slug
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页结果
     * @throws ResourceNotFoundException 标签不存在
     */
    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_POSTS_BY_TAG,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostsByTagBlocked"
    )
    public PageResult<PostVO> getPostsByTag(String slug, int page, int size) {
        log.debug("Getting posts by tag: slug={}, page={}, size={}", slug, page, size);
        
        // 1. 根据 slug 查询 Tag
        Tag tag = tagRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("标签不存在: " + slug));
        
        // 2. 分页查询 Tag 下的 Post ID 列表
        Pageable pageable = PageRequest.of(page, size);
        Page<Long> postIdPage = postTagRepository.findPostIdsByTagId(tag.getId(), pageable);
        
        // 即使当前页为空，也要返回正确的 total
        if (postIdPage.isEmpty()) {
            return PageResult.of(page, size, postIdPage.getTotalElements(), Collections.emptyList());
        }
        
        // 3. 批量查询 Post 详情（避免 N+1 问题）
        List<Long> postIds = postIdPage.getContent();
        Map<Long, Post> postMap = postRepository.findByIds(postIds);
        
        // 保持分页顺序
        List<Post> orderedPosts = postIds.stream()
                .map(postMap::get)
                .filter(post -> post != null)
                .collect(Collectors.toList());
        
        // 4. 组装 PostDTO 列表
        List<PostVO> postVOs = orderedPosts.stream()
                .map(PostViewAssembler::toVO)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, postIdPage.getTotalElements(), postVOs);
    }

    /**
     * 获取热门标签（按文章数量排序）
     * 
     * 实现策略：
     * 1. 先查询 Redis 缓存
     * 2. 缓存未命中则查询 tag_stats 表，按 post_count 降序排序
     * 3. 批量查询 Tag 详情
     * 4. 组装 TagStatsDTO 列表
     * 5. 写入 Redis 缓存（TTL: 1 hour）
     * 
     * Requirements: 4.4
     *
     * @param limit 返回数量限制
     * @return 热门标签列表
     */
    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_HOT_TAGS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetHotTagsBlocked"
    )
    public List<TagStatsDTO> getHotTags(int limit) {
        log.debug("Getting hot tags: limit={}", limit);

        try {
            CacheResult<List<TagStatsDTO>> cached = tagHotTagsStore.getHotTags(limit);
            if (cached.isNull()) {
                log.debug("Cache hit (empty list): limit={}", limit);
                return Collections.emptyList();
            }
            if (cached.isHit()) {
                log.debug("Cache hit: limit={}", limit);
                return cached.getValue();
            }

            // 3. 缓存未命中，查询数据库
            log.debug("Cache miss: limit={}", limit);
            List<TagStatsDTO> hotTags = queryHotTagsFromDatabase(limit);

            // 4. 写入缓存
            cacheHotTags(limit, hotTags);

            return hotTags;
        } catch (Exception e) {
            log.error("Cache operation failed for limit={}, falling back to database", limit, e);
            return queryHotTagsFromDatabase(limit);
        }
    }

    /**
     * 从数据库查询热门标签
     *
     * @param limit 返回数量限制
     * @return 热门标签列表
     */
    private List<TagStatsDTO> queryHotTagsFromDatabase(int limit) {
        // 1. 查询 tag_stats 表，按 post_count 降序排序
        List<Map<String, Object>> stats = tagRepository.findHotTags(limit);
        
        if (stats.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 2. 提取 Tag ID 列表
        List<Long> tagIds = stats.stream()
                .map(stat -> ((Number) stat.get("tag_id")).longValue())
                .collect(Collectors.toList());
        
        // 3. 批量查询 Tag 详情
        List<Tag> tags = tagRepository.findByIdIn(tagIds);
        
        // 创建 Tag ID 到 Tag 的映射
        Map<Long, Tag> tagMap = tags.stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        
        // 创建 Tag ID 到 post_count 的映射
        Map<Long, Integer> postCountMap = stats.stream()
                .collect(Collectors.toMap(
                        stat -> ((Number) stat.get("tag_id")).longValue(),
                        stat -> ((Number) stat.get("post_count")).intValue()
                ));
        
        // 4. 组装 TagStatsDTO 列表（保持排序）
        List<TagStatsDTO> result = new ArrayList<>();
        for (Long tagId : tagIds) {
            Tag tag = tagMap.get(tagId);
            if (tag != null) {
                result.add(TagStatsDTO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .postCount(postCountMap.getOrDefault(tagId, 0))
                        .build());
            }
        }
        
        return result;
    }

    /**
     * 缓存热门标签
     *
     * @param limit 热门标签数量限制
     * @param hotTags 热门标签列表
     */
    private void cacheHotTags(int limit, List<TagStatsDTO> hotTags) {
        try {
            if (hotTags != null && !hotTags.isEmpty()) {
                // 缓存实际值，添加随机抖动防止缓存雪崩
                Duration ttl = Duration.ofSeconds(CacheConstants.HOT_TAGS_CACHE_TTL_SECONDS + randomJitter());
                tagHotTagsStore.setHotTags(limit, hotTags, ttl);
                log.debug("Cached hot tags: limit={}, size={}, ttl={}s", limit, hotTags.size(), ttl.getSeconds());
            } else {
                // 缓存空值防止缓存穿透
                tagHotTagsStore.setEmptyHotTags(limit, Duration.ofSeconds(CacheConstants.NULL_VALUE_TTL_SECONDS));
                log.debug("Cached empty hot tags: limit={}", limit);
            }
        } catch (Exception e) {
            log.error("Failed to cache hot tags: limit={}", limit, e);
        }
    }

    /**
     * 失效热门标签缓存
     * 
     * 当 Tag 统计数据更新时调用
     */
    public void evictHotTagsCache() {
        try {
            tagHotTagsStore.evictHotTags();
            log.debug("Evicted hot tags cache");
        } catch (Exception e) {
            log.error("Failed to evict hot tags cache", e);
        }
    }

    /**
     * 生成随机抖动（0-60秒）
     *
     * @return 随机秒数
     */
    private long randomJitter() {
        return RANDOM.nextInt(CacheConstants.MAX_JITTER_SECONDS);
    }
}
