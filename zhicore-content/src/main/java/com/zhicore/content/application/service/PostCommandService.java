package com.zhicore.content.application.service;

import com.zhicore.content.application.command.CreatePostAppCommand;
import com.zhicore.content.application.command.SaveDraftCommand;
import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.command.commands.RestorePostCommand;
import com.zhicore.content.application.command.handlers.RestorePostHandler;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章写服务。
 *
 * 负责文章、草稿、标签与定时发布的写操作，不承载查询职责。
 */
@Service
@RequiredArgsConstructor
public class PostCommandService {

    private final PostApplicationService postApplicationService;
    private final RestorePostHandler restorePostHandler;

    public Long createPost(Long userId, CreatePostAppCommand request) {
        return postApplicationService.createPost(userId, request);
    }

    public void updatePost(Long userId, Long postId, UpdatePostAppCommand request) {
        postApplicationService.updatePost(userId, postId, request);
    }

    public void publishPost(Long userId, Long postId) {
        postApplicationService.publishPost(userId, postId);
    }

    public void unpublishPost(Long userId, Long postId) {
        postApplicationService.unpublishPost(userId, postId);
    }

    public void schedulePublish(Long userId, Long postId, LocalDateTime scheduledAt) {
        postApplicationService.schedulePublish(userId, postId, scheduledAt);
    }

    public void cancelSchedule(Long userId, Long postId) {
        postApplicationService.cancelSchedule(userId, postId);
    }

    public void deletePost(Long userId, Long postId) {
        postApplicationService.deletePost(userId, postId);
    }

    public void restorePost(Long userId, Long postId) {
        restorePostHandler.handle(new RestorePostCommand(
                com.zhicore.content.domain.model.PostId.of(postId),
                com.zhicore.content.domain.model.UserId.of(userId)
        ));
    }

    public void saveDraft(Long userId, Long postId, SaveDraftCommand request) {
        postApplicationService.saveDraft(postId, userId, request);
    }

    public void deleteDraft(Long userId, Long postId) {
        postApplicationService.deleteDraft(postId, userId);
    }

    public void attachTags(Long userId, Long postId, List<String> tags) {
        postApplicationService.replacePostTags(userId, postId, tags);
    }

    public void detachTag(Long userId, Long postId, String slug) {
        List<String> remainingTagNames = postApplicationService.getPostTags(postId).stream()
                .filter(tag -> !tag.getSlug().equals(slug))
                .map(com.zhicore.content.application.dto.TagDTO::getName)
                .toList();
        postApplicationService.replacePostTags(userId, postId, remainingTagNames);
    }

    public void consumeScheduledPublish(PostScheduleExecuteIntegrationEvent event) {
        postApplicationService.consumeScheduledPublish(event);
    }
}
