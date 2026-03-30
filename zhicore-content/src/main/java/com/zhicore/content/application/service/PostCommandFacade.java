package com.zhicore.content.application.service;

import com.zhicore.content.application.command.CreatePostAppCommand;
import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.command.commands.RestorePostCommand;
import com.zhicore.content.application.command.handlers.RestorePostHandler;
import com.zhicore.content.application.service.command.PostCreateCommandService;
import com.zhicore.content.application.service.command.PostDraftCommandService;
import com.zhicore.content.application.service.command.PostLifecycleCommandService;
import com.zhicore.content.application.service.command.PostPublishCommandService;
import com.zhicore.content.application.service.command.PostTagRelationCommandService;
import com.zhicore.content.application.service.command.PostUpdateCommandService;
import com.zhicore.content.application.service.command.ScheduledPublishCommandService;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章写门面。
 *
 * 为接口层收口文章命令入口，隐藏内部具体 use case service 的拆分细节。
 */
@Service
@RequiredArgsConstructor
public class PostCommandFacade {

    private final PostCreateCommandService postCreateCommandService;
    private final PostDraftCommandService postDraftCommandService;
    private final PostLifecycleCommandService postLifecycleCommandService;
    private final PostPublishCommandService postPublishCommandService;
    private final PostUpdateCommandService postUpdateCommandService;
    private final PostTagRelationCommandService postTagRelationCommandService;
    private final ScheduledPublishCommandService scheduledPublishCommandService;
    private final RestorePostHandler restorePostHandler;

    public Long createPost(Long userId, CreatePostAppCommand request) {
        return postCreateCommandService.createPost(userId, request);
    }

    public void updatePost(Long userId, Long postId, UpdatePostAppCommand request) {
        postUpdateCommandService.updatePost(userId, postId, request);
    }

    public void publishPost(Long userId, Long postId) {
        postPublishCommandService.publishPost(userId, postId);
    }

    public void unpublishPost(Long userId, Long postId) {
        postLifecycleCommandService.unpublishPost(userId, postId);
    }

    public void schedulePublish(Long userId, Long postId, OffsetDateTime scheduledAt) {
        scheduledPublishCommandService.schedulePublish(userId, postId, scheduledAt);
    }

    public void cancelSchedule(Long userId, Long postId) {
        scheduledPublishCommandService.cancelSchedule(userId, postId);
    }

    public void deletePost(Long userId, Long postId) {
        postLifecycleCommandService.deletePost(userId, postId);
    }

    public void restorePost(Long userId, Long postId) {
        restorePostHandler.handle(new RestorePostCommand(
                com.zhicore.content.domain.model.PostId.of(postId),
                com.zhicore.content.domain.model.UserId.of(userId)
        ));
    }

    public void saveDraft(Long userId, Long postId, SaveDraftCommand request) {
        postDraftCommandService.saveDraft(userId, postId, request);
    }

    public void deleteDraft(Long userId, Long postId) {
        postDraftCommandService.deleteDraft(userId, postId);
    }

    public void attachTags(Long userId, Long postId, List<String> tags) {
        postTagRelationCommandService.replacePostTags(userId, postId, tags);
    }

    public void detachTag(Long userId, Long postId, String slug) {
        postTagRelationCommandService.detachTag(userId, postId, slug);
    }

    public void consumeScheduledPublish(PostScheduleExecuteIntegrationEvent event) {
        scheduledPublishCommandService.consumeScheduledPublish(event);
    }
}
