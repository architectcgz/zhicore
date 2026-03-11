package com.zhicore.content.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.command.CreatePostAppCommand;
import com.zhicore.content.application.command.commands.CreatePostCommand;
import com.zhicore.content.application.mapper.EventMapper;
import com.zhicore.content.application.port.client.UserProfileClient;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.workflow.CreateDraftWorkflow;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.integration.messaging.post.PostCreatedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章创建写服务。
 *
 * 收口创建草稿所需的 ID 申请、作者快照、标签准备与事件转发，
 * 避免 PostWriteService 同时承担“创建用例”和“文章维护”职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostCreateCommandService {

    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final TagCommandService tagCommandService;
    private final PostCoverImageCommandService postCoverImageCommandService;
    private final UserProfileClient userProfileClient;
    private final CreateDraftWorkflow createDraftWorkflow;
    private final EventPublisher domainEventPublisher;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final EventMapper eventMapper;

    @Transactional
    public Long createPost(Long userId, CreatePostAppCommand request) {
        Long postId = generatePostId();
        postCoverImageCommandService.validateFileId(request.coverImageId());

        Set<TagId> tagIds = resolveTagIds(request.tags());
        OwnerSnapshot ownerSnapshot = getOwnerSnapshot(userId);

        CreatePostCommand command = new CreatePostCommand(
                PostId.of(postId),
                UserId.of(userId),
                request.title(),
                null,
                request.coverImageId(),
                request.content(),
                ContentType.MARKDOWN,
                ownerSnapshot,
                tagIds,
                request.topicId() != null ? TopicId.of(request.topicId()) : null
        );

        CreateDraftWorkflow.ExecutionResult result = createDraftWorkflow.execute(command);
        List<DomainEvent<?>> domainEvents = result.domainEvents();

        publishDomainEvents(postId, domainEvents);
        publishIntegrationEvents(postId, domainEvents);

        log.info("Draft created: postId={}, userId={}, ownerName={}",
                postId, userId, ownerSnapshot.getName());
        return postId;
    }

    private Long generatePostId() {
        ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
        if (!idResponse.isSuccess() || idResponse.getData() == null) {
            log.error("Failed to generate post ID: {}", idResponse.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成文章ID失败");
        }
        return idResponse.getData();
    }

    private Set<TagId> resolveTagIds(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return null;
        }
        if (tagNames.size() > 10) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单篇文章最多只能添加10个标签");
        }

        List<Tag> tags = tagCommandService.findOrCreateBatch(tagNames);
        return tags.stream()
                .map(tag -> TagId.of(tag.getId()))
                .collect(Collectors.toSet());
    }

    private OwnerSnapshot getOwnerSnapshot(Long userId) {
        return userProfileClient.getOwnerSnapshot(UserId.of(userId))
                .orElseGet(() -> OwnerSnapshot.createDefault(UserId.of(userId)));
    }

    private void publishDomainEvents(Long postId, List<DomainEvent<?>> domainEvents) {
        if (domainEvents.isEmpty()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<DomainEvent> rawEvents = (List) domainEvents;
        domainEventPublisher.publishBatch(rawEvents);
        log.info("Published {} domain events for post: postId={}", domainEvents.size(), postId);
    }

    private void publishIntegrationEvents(Long postId, List<DomainEvent<?>> domainEvents) {
        for (DomainEvent<?> domainEvent : domainEvents) {
            if (!(domainEvent instanceof PostCreatedDomainEvent createdEvent)) {
                continue;
            }

            PostCreatedIntegrationEvent integrationEvent = eventMapper.toIntegrationEvent(createdEvent);
            integrationEventPublisher.publish(integrationEvent);
            log.info("Published integration event to Outbox: eventId={}, postId={}",
                    integrationEvent.getEventId(), postId);
        }
    }
}
