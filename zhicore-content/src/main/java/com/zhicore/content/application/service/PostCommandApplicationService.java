package com.zhicore.content.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UploadFileClient;
import com.zhicore.api.client.UserBatchSimpleClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.command.CreatePostAppCommand;
import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.application.command.commands.CreatePostCommand;
import com.zhicore.content.application.command.commands.DeletePostCommand;
import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.command.handlers.DeletePostHandler;
import com.zhicore.content.application.command.commands.UpdatePostContentCommand;
import com.zhicore.content.application.command.handlers.UpdatePostContentHandler;
import com.zhicore.content.application.command.commands.UpdatePostMetaCommand;
import com.zhicore.content.application.command.handlers.UpdatePostMetaHandler;
import com.zhicore.content.application.mapper.EventMapper;
import com.zhicore.content.application.model.OutboxEventTypes;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.policy.ScheduledPublishPolicy;
import com.zhicore.content.application.workflow.CreateDraftWorkflow;
import com.zhicore.content.application.workflow.PublishPostWorkflow;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.service.DraftService;
import com.zhicore.content.domain.service.TagDomainService;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduledIntegrationEvent;
import com.zhicore.integration.messaging.post.PostTagsUpdatedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostUpdatedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章写应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostCommandApplicationService {

    private final PostRepository postRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final DraftService draftService;
    private final TagDomainService tagDomainService;
    private final PostTagRepository postTagRepository;
    private final UploadFileClient uploadServiceClient;
    private final UserBatchSimpleClient userServiceClient;
    
    // 新架构依赖
    private final CreateDraftWorkflow createDraftWorkflow;
    private final PublishPostWorkflow publishPostWorkflow;
    private final UpdatePostMetaHandler updatePostMetaHandler;
    private final UpdatePostContentHandler updatePostContentHandler;
    private final DeletePostHandler deletePostHandler;
    private final PostContentImageCleanupService postContentImageCleanupService;
    
    // 事件相关依赖
    private final EventPublisher domainEventPublisher;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final EventMapper eventMapper;

    // 定时发布（R1）
    private final ScheduledPublishEventStore scheduledPublishEventStore;
    private final ScheduledPublishPolicy scheduledPublishPolicy;

    // 告警 & DLQ（TASK-01）
    private final ContentAlertPort alertService;
    private final OutboxEventStore outboxEventStore;
    private final ObjectMapper objectMapper;

    /**
     * 创建文章（草稿）
     * 使用 CreateDraftWorkflow 实现两阶段提交保证数据一致性
     *
     * @param userId 用户ID
     * @param request 创建请求
     * @return 文章ID
     */
    @Transactional
    public Long createPost(Long userId, CreatePostAppCommand request) {
        // 生成文章ID
        ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
        if (!idResponse.isSuccess() || idResponse.getData() == null) {
            log.error("Failed to generate post ID: {}", idResponse.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成文章ID失败");
        }
        Long postId = idResponse.getData();

        // 验证封面图 fileId 格式
        if (request.coverImageId() != null && !request.coverImageId().isEmpty()) {
            validateFileId(request.coverImageId());
        }

        // 处理标签（查找或创建）
        Set<TagId> tagIds = null;
        if (request.tags() != null && !request.tags().isEmpty()) {
            // 验证标签数量
            if (request.tags().size() > 10) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "单篇文章最多只能添加10个标签");
            }
            
            // 查找或创建标签
            List<Tag> tags = tagDomainService.findOrCreateBatch(request.tags());
            tagIds = tags.stream()
                    .map(tag -> TagId.of(tag.getId()))
                    .collect(Collectors.toSet());
        }

        // 获取作者信息快照
        OwnerSnapshot ownerSnapshot = getOwnerSnapshot(userId);

        // 创建命令对象
        CreatePostCommand command = 
            new CreatePostCommand(
                PostId.of(postId),           // postId (值对象)
                UserId.of(userId),           // ownerId (值对象)
                request.title(),             // title
                null,                        // excerpt (自动生成)
                request.coverImageId(),      // coverImage
                request.content(),           // content
                ContentType.MARKDOWN,        // contentType
                ownerSnapshot,               // ownerSnapshot
                tagIds,                      // tagIds
                request.topicId() != null ? TopicId.of(request.topicId()) : null  // topicId
            );

        // 使用 CreateDraftWorkflow 执行创建（两阶段提交）
        CreateDraftWorkflow.ExecutionResult result = createDraftWorkflow.execute(command);

        // 标签与话题已在 workflow 中完成持久化，避免重复写入

        List<DomainEvent<?>> domainEvents = result.domainEvents();
        
        // 发布领域事件
        if (!domainEvents.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<DomainEvent> rawEvents = (List) domainEvents;
            domainEventPublisher.publishBatch(rawEvents);
            log.info("Published {} domain events for post: postId={}", domainEvents.size(), postId);
        }
        
        // 转换并发布集成事件（写 Outbox）
        for (DomainEvent<?> domainEvent : domainEvents) {
            if (domainEvent instanceof PostCreatedDomainEvent) {
                PostCreatedDomainEvent createdEvent = (PostCreatedDomainEvent) domainEvent;
                com.zhicore.integration.messaging.post.PostCreatedIntegrationEvent integrationEvent = 
                    eventMapper.toIntegrationEvent(createdEvent);
                integrationEventPublisher.publish(integrationEvent);
                log.info("Published integration event to Outbox: eventId={}, postId={}", 
                    integrationEvent.getEventId(), postId);
            }
        }

        log.info("Draft created: postId={}, userId={}, ownerName={}", 
            postId, userId, ownerSnapshot.getName());
        return postId;
    }

    /**
     * 获取作者信息快照
     * 
     * 从 user-service 获取作者信息并创建快照
     * 如果获取失败，使用默认值
     * 
     * @param userId 用户ID（作者ID）
     * @return 作者信息快照
     */
    private OwnerSnapshot getOwnerSnapshot(Long userId) {
        try {
            // 调用 user-service 获取用户信息
            ApiResponse<Map<Long, UserSimpleDTO>> response =
                userServiceClient.batchGetUsersSimple(Collections.singleton(userId));
            UserSimpleDTO userInfo = extractSingleUser(response, userId);

            if (userInfo != null) {
                return new OwnerSnapshot(
                    UserId.of(userId),
                    userInfo.getNickname(),
                    userInfo.getAvatarId(),
                    userInfo.getProfileVersion() != null ? userInfo.getProfileVersion() : 0L
                );
            } else {
                // user-service 返回失败，使用默认值
                log.warn("获取用户信息失败，使用默认值: userId={}, response={}", userId, response);
                return OwnerSnapshot.createDefault(UserId.of(userId));
            }
        } catch (Exception e) {
            // 调用 user-service 异常，使用默认值
            log.error("调用 user-service 异常，使用默认值: userId={}", userId, e);
            return OwnerSnapshot.createDefault(UserId.of(userId));
        }
    }

    /**
     * 更新文章
     * 拆分为更新元数据和更新内容两个调用
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @param request 更新请求
     */
    @Transactional
    public void updatePost(Long userId, Long postId, UpdatePostAppCommand request) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 如果更新封面图，删除旧的封面图
        if (request.coverImageId() != null && !request.coverImageId().isEmpty()) {
            // 验证新的 fileId 格式
            validateFileId(request.coverImageId());
            
            // 如果有旧的封面图且与新的不同，删除旧的
            String oldCoverImageId = post.getCoverImageId();
            if (oldCoverImageId != null && !oldCoverImageId.isEmpty() 
                    && !oldCoverImageId.equals(request.coverImageId())) {
                try {
                    uploadServiceClient.deleteFile(oldCoverImageId);
                    log.info("删除旧封面图: postId={}, fileId={}", postId, oldCoverImageId);
                } catch (Exception e) {
                    log.error("删除旧封面图失败: postId={}, fileId={}, error={}", 
                        postId, oldCoverImageId, e.getMessage(), e);
                    // 不影响主流程，继续执行
                }
            }
        }

        // 更新话题
        if (request.topicId() != null) {
            post.setTopic(TopicId.of(request.topicId()));
            postRepository.update(post);
        }

        // 处理标签更新（如果提供了标签列表）
        if (request.tags() != null) {
            // 获取旧标签
            List<Long> oldTagIds = postTagRepository.findTagIdsByPostId(postId);
            
            // 删除旧关联
            postTagRepository.detachAllByPostId(postId);
            
            // 创建新关联
            List<Long> newTagIds = Collections.emptyList();
            if (!request.tags().isEmpty()) {
                List<Tag> tags = tagDomainService.findOrCreateBatch(request.tags());
                newTagIds = tags.stream()
                        .map(Tag::getId)
                        .collect(Collectors.toList());
                postTagRepository.attachBatch(postId, newTagIds);
                log.info("Post tags updated: postId={}, tagCount={}", postId, newTagIds.size());
            } else {
                log.info("Post tags cleared: postId={}", postId);
            }
            
            integrationEventPublisher.publish(new PostTagsUpdatedIntegrationEvent(
                newEventId(),
                Instant.now(),
                postId,
                oldTagIds,
                newTagIds,
                Instant.now(),
                post.getVersion()
            ));
        }

        // 使用 UpdatePostMetaHandler 更新元数据
        UpdatePostMetaCommand metaCommand = 
            new UpdatePostMetaCommand(
                PostId.of(postId),
                UserId.of(userId),
                request.title(),
                post.getExcerpt(), // 使用当前的 excerpt，因为 request 中没有
                request.coverImageId()
            );
        updatePostMetaHandler.handle(metaCommand);

        // 使用 UpdatePostContentHandler 更新内容
        UpdatePostContentCommand contentCommand = 
            new UpdatePostContentCommand(
                PostId.of(postId),
                UserId.of(userId),
                request.content(),
                ContentType.MARKDOWN
            );
        updatePostContentHandler.handle(contentCommand);

        // 如果已发布，发布集成更新事件（Outbox）
        if (post.isPublished()) {
            integrationEventPublisher.publish(new PostUpdatedIntegrationEvent(
                    newEventId(),
                    Instant.now(),
                    post.getVersion(),
                    postId,
                    request.title(),
                    request.content(),
                    post.getExcerpt(),
                    Collections.emptyList()
            ));
        }

        log.info("Post updated: postId={}, userId={}", postId, userId);
    }

    /**
     * 替换文章的所有标签
     * 
     * 流程：
     * 1. 验证用户权限
     * 2. 查询文章
     * 3. 处理新标签（查找或创建）
     * 4. 删除旧关联
     * 5. 创建新关联
     * 6. 更新缓存（通过事件）
     * 7. 发布事件
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @param tagNames 新的标签名称列表
     */
    @Transactional
    public void replacePostTags(Long userId, Long postId, List<String> tagNames) {
        // 验证用户权限
        Post post = getPostAndCheckOwnership(postId, userId);

        // 验证标签数量
        if (tagNames != null && tagNames.size() > 10) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单篇文章最多只能添加10个标签");
        }

        // 删除旧关联
        List<Long> oldTagIds = postTagRepository.findTagIdsByPostId(postId);
        postTagRepository.detachAllByPostId(postId);
        log.info("Old post tags detached: postId={}", postId);

        // 创建新关联
        List<Long> newTagIds = Collections.emptyList();
        if (tagNames != null && !tagNames.isEmpty()) {
            // 查找或创建标签
            List<Tag> tags = tagDomainService.findOrCreateBatch(tagNames);
            newTagIds = tags.stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());
            
            // 批量创建关联
            postTagRepository.attachBatch(postId, newTagIds);
            log.info("New post tags attached: postId={}, tagCount={}", postId, newTagIds.size());
        }

        // 发布 PostTagsUpdated 事件（用于缓存失效和 MongoDB/ES 同步）
        integrationEventPublisher.publish(new PostTagsUpdatedIntegrationEvent(
            newEventId(),
            Instant.now(),
            postId,
            oldTagIds,
            newTagIds,
            Instant.now(),
            post.getVersion()
        ));

        log.info("Post tags replaced: postId={}, userId={}, newTagCount={}", 
                postId, userId, tagNames != null ? tagNames.size() : 0);
    }

    /**
     * 发布文章
     * 使用 PublishPostWorkflow 完成发布流程
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void publishPost(Long userId, Long postId) {
        // 使用 PublishPostWorkflow 执行发布
        List<DomainEvent<?>> domainEvents = publishPostWorkflow.execute(
            PostId.of(postId),
            UserId.of(userId)
        );
        
        // 发布领域事件
        if (!domainEvents.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<DomainEvent> rawEvents = (List) domainEvents;
            domainEventPublisher.publishBatch(rawEvents);
            log.info("Published {} domain events for post: postId={}", domainEvents.size(), postId);
        }
        
        // 转换并发布集成事件（写 Outbox）
        for (DomainEvent<?> domainEvent : domainEvents) {
            if (domainEvent instanceof PostPublishedDomainEvent) {
                PostPublishedDomainEvent publishedEvent = (PostPublishedDomainEvent) domainEvent;
                com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent integrationEvent = 
                    eventMapper.toIntegrationEvent(publishedEvent);
                integrationEventPublisher.publish(integrationEvent);
                log.info("Published integration event to Outbox: eventId={}, postId={}", 
                    integrationEvent.getEventId(), postId);
            }
        }

        log.info("Post published: postId={}, userId={}", postId, userId);
    }

    /**
     * 撤回文章（取消发布）
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void unpublishPost(Long userId, Long postId) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 撤回
        post.unpublish();

        // 保存
        postRepository.update(post);

        log.info("Post unpublished: postId={}, userId={}", postId, userId);
    }

    /**
     * 定时发布文章
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @param scheduledAt 定时发布时间
     */
    @Transactional
    public void schedulePublish(Long userId, Long postId, LocalDateTime scheduledAt) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 设置定时发布
        post.schedulePublish(scheduledAt);

        // 保存
        postRepository.update(post);

        // 统一以数据库时间计算延迟，避免应用节点时钟漂移
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        long initialDelaySeconds = java.time.Duration.between(dbNow, scheduledAt).getSeconds();
        int delayLevel = calculateDelayLevelBySeconds(Math.max(0, initialDelaySeconds));

        // 为本次“执行定时发布”创建跟踪事件（与 Outbox/MQ 的 event_id 对齐）
        String scheduleExecuteEventId = newEventId();
        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId(scheduleExecuteEventId)
                .postId(postId)
                .scheduledAt(scheduledAt)
                .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .lastEnqueueAt(dbNow)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(record);

        // 读取最新版本号作为 aggregateVersion（确保 Outbox 版本语义正确）
        Long aggregateVersion = postRepository.findById(postId)
                .map(Post::getVersion)
                .orElse(post.getVersion());

        // 发布延迟执行事件（通过 Outbox 投递）
        integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                scheduleExecuteEventId,
                Instant.now(),
                aggregateVersion,
                postId,
                userId,
                scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                delayLevel
        ));

        // 发布定时发布事件（通过 Outbox 投递）
        integrationEventPublisher.publish(new PostScheduledIntegrationEvent(
                newEventId(),
                Instant.now(),
                aggregateVersion,
                postId,
                userId,
                scheduledAt.atZone(ZoneId.systemDefault()).toInstant()
        ));

        log.info("Post scheduled: postId={}, userId={}, scheduledAt={}", postId, userId, scheduledAt);
    }

    /**
     * 取消定时发布
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void cancelSchedule(Long userId, Long postId) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 取消定时
        post.cancelSchedule();

        // 保存
        postRepository.update(post);

        log.info("Post schedule cancelled: postId={}, userId={}", postId, userId);
    }

    /**
     * 执行定时发布（由消息消费者调用）
     *
     * @param postId 文章ID
     */
    @Transactional
    public void executeScheduledPublish(Long postId) {
        // 兼容旧调用：没有 eventId 时，尽量按 postId 找到活跃事件进行处理
        PostScheduleExecuteIntegrationEvent synthetic = new PostScheduleExecuteIntegrationEvent(
                newEventId(),
                Instant.now(),
                0L,
                postId,
                null,
                Instant.now(),
                0
        );
        consumeScheduledPublish(synthetic);
    }

    /**
     * 消费“定时发布执行”事件（R1）
     *
     * 关键点：
     * - 必须使用数据库时间做门禁（db_now < scheduled_at 时不发布）
     * - 发布操作必须为单条条件更新（幂等）
     * - 未到点需按退避策略重入队，超过上限则转入 SCHEDULED_PENDING 由扫描任务兜底
     */
    @Transactional
    public void consumeScheduledPublish(PostScheduleExecuteIntegrationEvent message) {
        Long postId = message.getPostId();

        LocalDateTime appNow = LocalDateTime.now();
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        LocalDateTime scheduledAt = LocalDateTime.ofInstant(message.getScheduledAt(), ZoneId.systemDefault());

        ScheduledPublishEventRecord record = scheduledPublishEventStore.findByEventId(message.getEventId())
                .or(() -> scheduledPublishEventStore.findActiveByPostId(postId))
                .orElseGet(() -> {
                    ScheduledPublishEventRecord created = ScheduledPublishEventRecord.builder()
                            .eventId(message.getEventId())
                            .postId(postId)
                            .scheduledAt(scheduledAt)
                            .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                            .rescheduleRetryCount(0)
                            .publishRetryCount(0)
                            .lastEnqueueAt(null)
                            .createdAt(dbNow)
                            .updatedAt(dbNow)
                            .build();
                    scheduledPublishEventStore.save(created);
                    return created;
                });

        // 以记录内的 scheduledAt 为准（避免消息乱序/重复导致的覆盖）
        LocalDateTime effectiveScheduledAt = record.getScheduledAt();
        if (effectiveScheduledAt == null) {
            effectiveScheduledAt = scheduledAt;
        }

        log.info("Consume scheduled publish: postId={}, scheduledAt={}, appNow={}, dbNow={}, rescheduleRetry={}, publishRetry={}, status={}",
                postId,
                effectiveScheduledAt,
                appNow,
                dbNow,
                record.getRescheduleRetryCount(),
                record.getPublishRetryCount(),
                record.getStatus());

        if (dbNow.isBefore(effectiveScheduledAt)) {
            handleNotDue(record, dbNow, effectiveScheduledAt, message);
            return;
        }

        handleDue(record, dbNow, effectiveScheduledAt);
        return;
    }

    private void handleNotDue(
            ScheduledPublishEventRecord record,
            LocalDateTime dbNow,
            LocalDateTime scheduledAt,
            PostScheduleExecuteIntegrationEvent message
    ) {
        long remainingSeconds = java.time.Duration.between(dbNow, scheduledAt).getSeconds();
        int currentRetry = record.getRescheduleRetryCount() != null ? record.getRescheduleRetryCount() : 0;

        if (currentRetry < scheduledPublishPolicy.maxRescheduleRetries()) {
            long backoffMinutes = 1L << Math.min(currentRetry, 20); // 防止位移溢出
            long targetDelaySeconds = Math.min(
                    remainingSeconds,
                    Math.min(backoffMinutes * 60, scheduledPublishPolicy.maxDelayMinutes() * 60L)
            );

            int delayLevel = calculateDelayLevelBySeconds(Math.max(1, targetDelaySeconds));
            String newEventId = newEventId();

            record = record.withEventId(newEventId)
                    .withRescheduleRetryCount(currentRetry + 1)
                    .withLastEnqueueAt(dbNow)
                    .withUpdatedAt(dbNow)
                    .withLastError("未到点，重入队 remainingSeconds=" + remainingSeconds + ", delayLevel=" + delayLevel);
            scheduledPublishEventStore.update(record);

            Long aggregateVersion = postRepository.findById(record.getPostId())
                    .map(Post::getVersion)
                    .orElse(0L);

            integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                    newEventId,
                    Instant.now(),
                    aggregateVersion,
                    record.getPostId(),
                    message.getAuthorId(),
                    scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                    delayLevel
            ));
            return;
        }

        record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.SCHEDULED_PENDING)
                .withUpdatedAt(dbNow)
                .withLastError("未到点且重入队达到上限，转入 SCHEDULED_PENDING 由扫描任务兜底");
        scheduledPublishEventStore.update(record);
    }

    private void handleDue(ScheduledPublishEventRecord record, LocalDateTime dbNow, LocalDateTime scheduledAt) {
        try {
            Optional<Long> newVersion = postRepository.publishScheduledIfNeeded(record.getPostId(), dbNow);
            if (newVersion.isPresent()) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PUBLISHED)
                        .withUpdatedAt(dbNow)
                        .withLastError(null);
                scheduledPublishEventStore.update(record);

                integrationEventPublisher.publish(new PostPublishedIntegrationEvent(
                        newEventId(),
                        Instant.now(),
                        record.getPostId(),
                        dbNow.atZone(ZoneId.systemDefault()).toInstant(),
                        newVersion.get()
                ));
                return;
            }

            // 幂等 no-op：区分已发布 vs 非法状态/不存在
            Post existing = postRepository.findById(record.getPostId()).orElse(null);
            if (existing == null) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
                        .withUpdatedAt(dbNow)
                        .withLastError("发布 no-op 且文章不存在");
                scheduledPublishEventStore.update(record);
                return;
            }

            if (existing.getStatus() == PostStatus.PUBLISHED) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PUBLISHED)
                        .withUpdatedAt(dbNow)
                        .withLastError(null);
                scheduledPublishEventStore.update(record);
                return;
            }

            record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
                    .withUpdatedAt(dbNow)
                    .withLastError("发布 no-op 且文章状态非法: " + existing.getStatus());
            scheduledPublishEventStore.update(record);
        } catch (Exception e) {
            int currentRetry = record.getPublishRetryCount() != null ? record.getPublishRetryCount() : 0;
            if (currentRetry < scheduledPublishPolicy.maxPublishRetries()) {
                long backoffMinutes = 1L << Math.min(currentRetry, 20);
                long targetDelaySeconds = Math.min(backoffMinutes * 60, scheduledPublishPolicy.maxDelayMinutes() * 60L);
                int delayLevel = calculateDelayLevelBySeconds(Math.max(1, targetDelaySeconds));

                String newEventId = newEventId();
                record = record.withEventId(newEventId)
                        .withPublishRetryCount(currentRetry + 1)
                        .withLastEnqueueAt(dbNow)
                        .withUpdatedAt(dbNow)
                        .withLastError("发布失败重试: " + e.getMessage());
                scheduledPublishEventStore.update(record);

                Long aggregateVersion = postRepository.findById(record.getPostId())
                        .map(Post::getVersion)
                        .orElse(0L);

                integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                        newEventId,
                        Instant.now(),
                        aggregateVersion,
                        record.getPostId(),
                        null,
                        scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                        delayLevel
                ));
                return;
            }

            record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
                    .withUpdatedAt(dbNow)
                    .withLastError("发布失败且达到重试上限: " + e.getMessage());
            scheduledPublishEventStore.update(record);

            // 投递 DLQ 与告警（TASK-01）
            try {
                alertService.alertScheduledPublishFailedAfterRetries(
                        record.getPostId(),
                        record.getLastError(),
                        record.getPublishRetryCount()
                );
            } catch (Exception alertEx) {
                log.error("Failed to send scheduled publish failure alert: postId={}", record.getPostId(), alertEx);
            }

            try {
                emitScheduledPublishDlqEvent(record, scheduledAt, e);
            } catch (Exception dlqEx) {
                log.error("Failed to emit scheduled publish DLQ outbox event: postId={}", record.getPostId(), dlqEx);
            }

            log.error("Scheduled publish failed after retries: postId={}, error={}", record.getPostId(), e.getMessage(), e);
        }
    }

    private void emitScheduledPublishDlqEvent(ScheduledPublishEventRecord record, LocalDateTime scheduledAt, Exception e)
            throws Exception {
        if (record == null || record.getPostId() == null) {
            return;
        }

        Long postId = record.getPostId();
        Long aggregateVersion = postRepository.findById(postId)
                .map(Post::getVersion)
                .orElse(0L);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", postId);
        payload.put("scheduledTime", scheduledAt != null
                ? scheduledAt.atZone(ZoneId.systemDefault()).toInstant().toString()
                : null);
        payload.put("failReason", e != null ? e.getMessage() : null);
        payload.put("retryCount", record.getPublishRetryCount());
        payload.put("lastError", record.getLastError());

        Instant now = Instant.now();
        OutboxEventRecord entity = OutboxEventRecord.builder()
                .eventId(newEventId())
                .eventType(OutboxEventTypes.SCHEDULED_PUBLISH_DLQ)
                .aggregateId(postId)
                .aggregateVersion(aggregateVersion)
                .schemaVersion(1)
                .payload(objectMapper.writeValueAsString(payload))
                .occurredAt(now)
                .createdAt(now)
                .updatedAt(now)
                .retryCount(0)
                .status(OutboxEventRecord.OutboxStatus.PENDING)
                .build();

        outboxEventStore.save(entity);
        log.warn("Scheduled publish DLQ outbox event created: eventId={}, postId={}", entity.getEventId(), postId);
    }

    /**
     * 删除文章（软删除）
     * 使用 DeletePostHandler 处理删除逻辑
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 删除封面图
        if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
            try {
                uploadServiceClient.deleteFile(post.getCoverImageId());
                log.info("删除封面图: postId={}, fileId={}", postId, post.getCoverImageId());
            } catch (Exception e) {
                log.error("删除封面图失败: postId={}, fileId={}, error={}", 
                    postId, post.getCoverImageId(), e.getMessage(), e);
                // 不影响主流程，继续执行
            }
        }

        // 使用 DeletePostHandler 软删除
        DeletePostCommand command = 
            new DeletePostCommand(
                PostId.of(postId),
                UserId.of(userId)
            );
        deletePostHandler.handle(command);

        // 异步清理正文图片（best-effort，不阻塞删除主流程）
        try {
            postContentImageCleanupService.cleanupContentImagesAsync(postId);
        } catch (Exception e) {
            log.warn("Failed to schedule content image cleanup: postId={}", postId, e);
        }

        log.info("Post deleted: postId={}, userId={}", postId, userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取文章并检查所有权
     */
    private Post getPostAndCheckOwnership(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        if (!post.isOwnedBy(UserId.of(userId))) {
            throw new ForbiddenException("无权操作此文章");
        }

        return post;
    }

    /**
     * 按秒数计算 RocketMQ 延迟级别（用于 DB 时间基准的重试/重入队）
     */
    private int calculateDelayLevelBySeconds(long delaySeconds) {
        if (delaySeconds <= 1) return 1;      // 1s
        if (delaySeconds <= 5) return 2;      // 5s
        if (delaySeconds <= 10) return 3;     // 10s
        if (delaySeconds <= 30) return 4;     // 30s
        if (delaySeconds <= 60) return 5;     // 1m
        if (delaySeconds <= 120) return 6;    // 2m
        if (delaySeconds <= 180) return 7;    // 3m
        if (delaySeconds <= 240) return 8;    // 4m
        if (delaySeconds <= 300) return 9;    // 5m
        if (delaySeconds <= 360) return 10;   // 6m
        if (delaySeconds <= 420) return 11;   // 7m
        if (delaySeconds <= 480) return 12;   // 8m
        if (delaySeconds <= 540) return 13;   // 9m
        if (delaySeconds <= 600) return 14;   // 10m
        if (delaySeconds <= 1200) return 15;  // 20m
        if (delaySeconds <= 1800) return 16;  // 30m
        if (delaySeconds <= 3600) return 17;  // 1h
        return 18;                             // 2h (max)
    }

    // ==================== 草稿管理方法 ====================

    /**
     * 保存草稿
     * 支持自动保存和手动保存，每个用户每篇文章只保留一份草稿（Upsert模式）
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @param request 保存草稿请求
     */
    public void saveDraft(Long postId, Long userId, SaveDraftCommand request) {
        // 验证文章存在且用户有权限
        Post post = getPostAndCheckOwnership(postId, userId);
        
        // 调用 DraftManager 保存草稿
        draftService.saveDraft(
            postId,
            userId,
            request.content(),
            request.autoSave() != null ? request.autoSave() : false
        );
        
        log.info("Draft saved: postId={}, userId={}, isAutoSave={}", 
            postId, userId, request.autoSave());
    }

    /**
     * 删除草稿
     * 当用户主动删除草稿或发布文章时调用
     *
     * @param postId 文章ID
     * @param userId 用户ID
     */
    public void deleteDraft(Long postId, Long userId) {
        // 验证文章存在且用户有权限
        Post post = getPostAndCheckOwnership(postId, userId);
        
        // 调用 DraftManager 删除草稿
        draftService.deleteDraft(postId, userId);
        
        log.info("Draft deleted: postId={}, userId={}", postId, userId);
    }

    private UserSimpleDTO extractSingleUser(ApiResponse<Map<Long, UserSimpleDTO>> response, Long userId) {
        if (response == null || !response.isSuccess() || response.getData() == null || userId == null) {
            return null;
        }
        return response.getData().get(userId);
    }

    /**
     * 验证 fileId 格式（UUIDv7）
     * 
     * UUIDv7 格式：8-4-4-4-12 字符，第13位必须为7
     * 
     * @param fileId 文件ID
     * @throws IllegalArgumentException 如果格式无效
     */
    private void validateFileId(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return; // 允许为空
        }
        
        // UUIDv7 格式验证：8-4-4-4-12，第13位为7
        String uuidv7Pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
        if (!fileId.matches(uuidv7Pattern)) {
            throw new IllegalArgumentException("无效的 fileId 格式: " + fileId);
        }
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}


