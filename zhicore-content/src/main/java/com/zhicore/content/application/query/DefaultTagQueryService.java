package com.zhicore.content.application.query;

import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import com.zhicore.content.application.query.view.TagListItemView;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 标签查询服务实现
 * 
 * 负责从数据源查询标签数据，不包含缓存逻辑
 * 缓存由 CacheAsideTagQuery 装饰器处理
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service("tagSourceQuery")
@RequiredArgsConstructor
public class DefaultTagQueryService implements TagQuery {
    
    private final TagRepository tagRepository;
    
    @Override
    public TagDetailView getDetail(Long tagId) {
        Optional<Tag> tagOpt = tagRepository.findById(tagId);
        if (tagOpt.isEmpty()) {
            log.warn("Tag not found: {}", tagId);
            return null;
        }
        
        Tag tag = tagOpt.get();
        return toDetailView(tag);
    }
    
    @Override
    public TagDetailView getDetailBySlug(String slug) {
        Optional<Tag> tagOpt = tagRepository.findBySlug(slug);
        if (tagOpt.isEmpty()) {
            log.warn("Tag not found by slug: {}", slug);
            return null;
        }
        
        Tag tag = tagOpt.get();
        return toDetailView(tag);
    }
    
    @Override
    public List<TagListItemView> getList(int limit) {
        // 使用分页查询获取标签列表
        List<Tag> tags = tagRepository.findAll(PageRequest.of(0, limit)).getContent();
        return toListItemViews(tags);
    }
    
    @Override
    public List<TagListItemView> searchByName(String keyword, int limit) {
        List<Tag> tags = tagRepository.searchByName(keyword, limit);
        return toListItemViews(tags);
    }
    
    @Override
    public List<HotTagView> getHotTags(int limit) {
        // 获取热门标签统计信息
        List<Map<String, Object>> hotTagStats = tagRepository.findHotTags(limit);
        
        if (hotTagStats.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 提取标签 ID 列表
        List<Long> tagIds = hotTagStats.stream()
                .map(stat -> ((Number) stat.get("tag_id")).longValue())
                .collect(Collectors.toList());
        
        // 批量查询标签信息
        List<Tag> tags = tagRepository.findByIdIn(tagIds);
        Map<Long, Tag> tagMap = tags.stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));
        
        // 组装热门标签视图
        return hotTagStats.stream()
                .map(stat -> {
                    Long tagId = ((Number) stat.get("tag_id")).longValue();
                    Long postCount = ((Number) stat.get("post_count")).longValue();
                    Tag tag = tagMap.get(tagId);
                    
                    if (tag == null) {
                        log.warn("Tag not found for hot tag stat: {}", tagId);
                        return null;
                    }
                    
                    return HotTagView.builder()
                            .id(tag.getId())
                            .name(tag.getName())
                            .slug(tag.getSlug())
                            .postCount(postCount)
                            .build();
                })
                .filter(view -> view != null)
                .collect(Collectors.toList());
    }
    
    /**
     * 将 Tag 转换为 TagDetailView
     * 
     * @param tag 标签实例
     * @return 标签详情视图
     */
    private TagDetailView toDetailView(Tag tag) {
        return TagDetailView.builder()
                .id(tag.getId())
                .name(tag.getName())
                .slug(tag.getSlug())
                .description(tag.getDescription())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }
    
    /**
     * 将 Tag 列表转换为 TagListItemView 列表
     * 
     * @param tags 标签列表
     * @return 视图列表
     */
    private List<TagListItemView> toListItemViews(List<Tag> tags) {
        if (tags.isEmpty()) {
            return Collections.emptyList();
        }
        
        return tags.stream()
                .map(tag -> TagListItemView.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .description(tag.getDescription())
                        .build())
                .collect(Collectors.toList());
    }
}
