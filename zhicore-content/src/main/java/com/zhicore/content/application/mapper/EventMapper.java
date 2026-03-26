package com.zhicore.content.application.mapper;

import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.integration.messaging.post.PostCreatedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostTagsUpdatedIntegrationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 事件映射器
 * 
 * 负责将领域事件转换为集成事件
 * 
 * 转换规则：
 * 1. 保持 eventId 和 occurredAt 一致
 * 2. 值对象转换为原始类型（Long、String）
 * 3. 集合转换（Set<TagId> → List<Long>）
 * 4. 空值处理
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class EventMapper {
    
    /**
     * 转换 PostCreatedDomainEvent 为 PostCreatedIntegrationEvent
     * 
     * @param domainEvent 领域事件
     * @return 集成事件
     */
    public PostCreatedIntegrationEvent toIntegrationEvent(PostCreatedDomainEvent domainEvent) {
        if (domainEvent == null) {
            return null;
        }
        
        // 转换标签ID集合（Set<TagId> → List<Long>）
        List<Long> tagIds = domainEvent.getTagIds() != null
            ? domainEvent.getTagIds().stream()
                .map(TagId::getValue)
                .collect(Collectors.toList())
            : null;
        
        // 转换话题ID（TopicId → Long）
        Long topicId = domainEvent.getTopicId() != null
            ? domainEvent.getTopicId().getValue()
            : null;
        
        return new PostCreatedIntegrationEvent(
            domainEvent.getEventId(),
            domainEvent.getOccurredAt(),
            domainEvent.getPostId().getValue(),
            domainEvent.getTitle(),
            domainEvent.getExcerpt(),
            domainEvent.getAuthorId().getValue(),
            domainEvent.getAuthorName(),
            tagIds,
            topicId,
            domainEvent.getTopicName(),
            domainEvent.getStatus(),
            null,  // publishedAt 为 null（草稿状态）
            domainEvent.getCreatedAt(),
            domainEvent.getAggregateVersion()
        );
    }
    
    /**
     * 转换 PostPublishedDomainEvent 为 PostPublishedIntegrationEvent
     * 
     * @param domainEvent 领域事件
     * @return 集成事件
     */
    public PostPublishedIntegrationEvent toIntegrationEvent(PostPublishedDomainEvent domainEvent) {
        if (domainEvent == null) {
            return null;
        }
        
        return new PostPublishedIntegrationEvent(
            domainEvent.getEventId(),
            domainEvent.getOccurredAt(),
            domainEvent.getPostId().getValue(),
            domainEvent.getPublishedAt(),
            domainEvent.getAggregateVersion()
        );
    }

    public PostPublishedIntegrationEvent toIntegrationEvent(PostPublishedDomainEvent domainEvent,
                                                            Long authorId,
                                                            String title,
                                                            String excerpt) {
        if (domainEvent == null) {
            return null;
        }

        return new PostPublishedIntegrationEvent(
                domainEvent.getEventId(),
                domainEvent.getOccurredAt(),
                domainEvent.getPostId().getValue(),
                authorId,
                title,
                excerpt,
                domainEvent.getPublishedAt(),
                domainEvent.getAggregateVersion()
        );
    }
    
    /**
     * 转换 PostTagsUpdatedDomainEvent 为 PostTagsUpdatedIntegrationEvent
     * 
     * @param domainEvent 领域事件
     * @return 集成事件
     */
    public PostTagsUpdatedIntegrationEvent toIntegrationEvent(PostTagsUpdatedDomainEvent domainEvent) {
        if (domainEvent == null) {
            return null;
        }
        
        // 转换旧标签ID集合（Set<TagId> → List<Long>）
        List<Long> oldTagIds = convertTagIds(domainEvent.getOldTagIds());
        
        // 转换新标签ID集合（Set<TagId> → List<Long>）
        List<Long> newTagIds = convertTagIds(domainEvent.getNewTagIds());
        
        return new PostTagsUpdatedIntegrationEvent(
            domainEvent.getEventId(),
            domainEvent.getOccurredAt(),
            domainEvent.getPostId().getValue(),
            oldTagIds,
            newTagIds,
            domainEvent.getUpdatedAt(),
            domainEvent.getAggregateVersion()
        );
    }
    
    /**
     * 转换标签ID集合
     * 
     * @param tagIds 标签ID集合（值对象）
     * @return 标签ID列表（原始类型）
     */
    private List<Long> convertTagIds(Set<TagId> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return null;
        }
        
        return tagIds.stream()
            .map(TagId::getValue)
            .collect(Collectors.toList());
    }
    
    /**
     * 转换 LocalDateTime 为 Instant
     * 
     * @param localDateTime LocalDateTime
     * @return Instant（UTC）
     */
    private Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
