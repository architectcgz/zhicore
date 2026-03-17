package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.dto.TagStatsDTO;
import com.zhicore.content.application.query.TagQuery;
import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import com.zhicore.content.application.query.view.TagListItemView;
import com.zhicore.content.application.service.query.TagListQueryService;
import com.zhicore.content.application.service.query.TagPostQueryService;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签查询门面。
 *
 * 对外提供标签相关查询能力，并负责接口层 DTO 转换。
 * 其中详情、搜索、热门标签统一经 TagQuery 读模型获取；
 * 分页列表与标签文章分页经独立 query service 获取，
 * 保持 facade 只做读入口编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagQueryFacade {

    private final TagQuery tagQuery;
    private final TagListQueryService tagListQueryService;
    private final TagPostQueryService tagPostQueryService;

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_TAG_DETAIL,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetTagDetailBlocked"
    )
    public TagDTO getTag(String slug) {
        log.debug("Getting tag by slug via query: {}", slug);
        TagDetailView detailView = tagQuery.getDetailBySlug(slug);
        if (detailView == null) {
            throw new ResourceNotFoundException("标签不存在: " + slug);
        }
        return toTagDTO(detailView);
    }

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.LIST_TAGS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleListTagsBlocked"
    )
    public PageResult<TagDTO> listTags(int page, int size) {
        return tagListQueryService.listTags(page, size);
    }

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.SEARCH_TAGS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleSearchTagsBlocked"
    )
    public List<TagDTO> searchTags(String keyword, int limit) {
        log.debug("Searching tags via query: keyword={}, limit={}", keyword, limit);
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return tagQuery.searchByName(keyword.trim(), limit).stream()
                .map(this::toTagDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_POSTS_BY_TAG,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostsByTagBlocked"
    )
    public PageResult<PostVO> getPostsByTag(String slug, int page, int size) {
        return tagPostQueryService.getPostsByTag(slug, page, size);
    }

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_HOT_TAGS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetHotTagsBlocked"
    )
    public List<TagStatsDTO> getHotTags(int limit) {
        log.debug("Getting hot tags via query: limit={}", limit);
        return tagQuery.getHotTags(limit).stream()
                .map(this::toTagStatsDTO)
                .collect(Collectors.toList());
    }

    private TagDTO toTagDTO(TagDetailView detailView) {
        return TagDTO.builder()
                .id(detailView.getId())
                .name(detailView.getName())
                .slug(detailView.getSlug())
                .description(detailView.getDescription())
                .createdAt(detailView.getCreatedAt())
                .updatedAt(detailView.getUpdatedAt())
                .build();
    }

    private TagDTO toTagDTO(TagListItemView itemView) {
        return TagDTO.builder()
                .id(itemView.getId())
                .name(itemView.getName())
                .slug(itemView.getSlug())
                .description(itemView.getDescription())
                .build();
    }

    private TagStatsDTO toTagStatsDTO(HotTagView hotTagView) {
        return TagStatsDTO.builder()
                .id(hotTagView.getId())
                .name(hotTagView.getName())
                .slug(hotTagView.getSlug())
                .postCount(hotTagView.getPostCount().intValue())
                .build();
    }
}
