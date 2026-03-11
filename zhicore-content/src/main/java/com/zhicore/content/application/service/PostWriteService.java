package com.zhicore.content.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.application.command.commands.DeletePostCommand;
import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.command.handlers.DeletePostHandler;
import com.zhicore.content.application.command.commands.UpdatePostContentCommand;
import com.zhicore.content.application.command.handlers.UpdatePostContentHandler;
import com.zhicore.content.application.command.commands.UpdatePostMetaCommand;
import com.zhicore.content.application.command.handlers.UpdatePostMetaHandler;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.service.DraftCommandService;
import com.zhicore.integration.messaging.post.PostTagsUpdatedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostUpdatedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 文章写应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostWriteService {

    private final PostRepository postRepository;
    private final DraftCommandService draftCommandService;
    private final PostTagCommandService postTagCommandService;
    private final PostCoverImageCommandService postCoverImageCommandService;
    private final UpdatePostMetaHandler updatePostMetaHandler;
    private final UpdatePostContentHandler updatePostContentHandler;
    private final DeletePostHandler deletePostHandler;
    private final PostContentImageCleanupService postContentImageCleanupService;
    private final IntegrationEventPublisher integrationEventPublisher;

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
        postCoverImageCommandService.handleCoverImageChange(postId, post.getCoverImageId(), request.coverImageId());

        // 更新话题
        if (request.topicId() != null) {
            post.setTopic(TopicId.of(request.topicId()));
            postRepository.update(post);
        }

        // 处理标签更新（如果提供了标签列表）
        if (request.tags() != null) {
            PostTagCommandService.ReplaceResult replaceResult = postTagCommandService.replaceTags(postId, request.tags());
            
            integrationEventPublisher.publish(new PostTagsUpdatedIntegrationEvent(
                newEventId(),
                Instant.now(),
                postId,
                replaceResult.oldTagIds(),
                replaceResult.newTagIds(),
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

        PostTagCommandService.ReplaceResult replaceResult = postTagCommandService.replaceTags(postId, tagNames);

        // 发布 PostTagsUpdated 事件（用于缓存失效和 MongoDB/ES 同步）
        integrationEventPublisher.publish(new PostTagsUpdatedIntegrationEvent(
            newEventId(),
            Instant.now(),
            postId,
            replaceResult.oldTagIds(),
            replaceResult.newTagIds(),
            Instant.now(),
            post.getVersion()
        ));

        log.info("Post tags replaced: postId={}, userId={}, newTagCount={}", 
                postId, userId, tagNames != null ? tagNames.size() : 0);
    }

    /**
     * 移除文章指定标签。
     *
     * 基于当前标签关联计算剩余标签后复用替换逻辑，避免 command 层反向依赖 read service。
     *
     * @param userId 用户 ID
     * @param postId 文章 ID
     * @param slug 标签 slug
     */
    @Transactional
    public void detachTag(Long userId, Long postId, String slug) {
        getPostAndCheckOwnership(postId, userId);
        List<String> remainingTagNames = postTagCommandService.listRemainingTagNames(postId, slug);
        replacePostTags(userId, postId, remainingTagNames);
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
        postCoverImageCommandService.deleteCoverImage(postId, post.getCoverImageId());

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
        draftCommandService.saveDraft(
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
        draftCommandService.deleteDraft(postId, userId);
        
        log.info("Draft deleted: postId={}, userId={}", postId, userId);
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}


