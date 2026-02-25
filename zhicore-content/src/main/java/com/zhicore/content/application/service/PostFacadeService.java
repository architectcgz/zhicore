package com.zhicore.content.application.service;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.application.command.commands.RestorePostCommand;
import com.zhicore.content.application.command.handlers.RestorePostHandler;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostContent;
import com.zhicore.content.interfaces.dto.request.AttachTagsRequest;
import com.zhicore.content.interfaces.dto.request.CreatePostRequest;
import com.zhicore.content.interfaces.dto.request.SaveDraftRequest;
import com.zhicore.content.interfaces.dto.request.SchedulePublishRequest;
import com.zhicore.content.interfaces.dto.request.UpdatePostRequest;
import com.zhicore.content.interfaces.dto.response.DraftVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 面向接口层的文章门面服务。
 */
@Service
@RequiredArgsConstructor
public class PostFacadeService {

    private final RestorePostHandler restorePostHandler;
    private final PostApplicationService postApplicationService;

    public Long createPost(Long userId, CreatePostRequest request) {
        return postApplicationService.createPost(userId, request);
    }

    public void updatePost(Long userId, Long postId, UpdatePostRequest request) {
        postApplicationService.updatePost(userId, postId, request);
    }

    public void publishPost(Long userId, Long postId) {
        postApplicationService.publishPost(userId, postId);
    }

    public void unpublishPost(Long userId, Long postId) {
        postApplicationService.unpublishPost(userId, postId);
    }

    public void schedulePublish(Long userId, Long postId, SchedulePublishRequest request) {
        postApplicationService.schedulePublish(userId, postId, request.getScheduledAt());
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

    public PostVO getPost(Long postId) {
        return postApplicationService.getPostById(postId);
    }

    public PostVO getMyPost(Long userId, Long postId) {
        return postApplicationService.getUserPostById(userId, postId);
    }

    public List<PostBriefVO> getMyPosts(Long userId, String status, int page, int size) {
        return postApplicationService.getUserPosts(userId, PostStatus.valueOf(status), page, size);
    }

    public HybridPageResult<PostBriefVO> getPublishedPosts(Integer page, int size) {
        HybridPageRequest request = new HybridPageRequest();
        request.setPage(page);
        request.setSize(size);
        return postApplicationService.getPublishedPostsHybrid(request);
    }

    public HybridPageResult<PostBriefVO> getPostList(PostListQuery query) {
        return postApplicationService.getPostList(query);
    }

    public List<PostBriefVO> getPublishedPostsCursor(LocalDateTime cursor, int size) {
        return postApplicationService.getPublishedPostsCursor(cursor, size);
    }

    public HybridPageResult<PostBriefVO> getPublishedPostsHybrid(HybridPageRequest request) {
        return postApplicationService.getPublishedPostsHybrid(request);
    }

    public Map<Long, PostDTO> batchGetPosts(Set<Long> postIds) {
        return postApplicationService.batchGetPosts(postIds);
    }

    public PostContent getPostContent(Long postId) {
        return postApplicationService.getPostContent(postId);
    }

    public void saveDraft(Long userId, Long postId, SaveDraftRequest request) {
        postApplicationService.saveDraft(postId, userId, request);
    }

    public DraftVO getDraft(Long userId, Long postId) {
        return postApplicationService.getDraft(postId, userId);
    }

    public List<DraftVO> getUserDrafts(Long userId) {
        return postApplicationService.getUserDrafts(userId);
    }

    public void deleteDraft(Long userId, Long postId) {
        postApplicationService.deleteDraft(postId, userId);
    }

    public void attachTags(Long userId, Long postId, AttachTagsRequest request) {
        postApplicationService.replacePostTags(userId, postId, request.getTags());
    }

    public void detachTag(Long userId, Long postId, String slug) {
        List<String> remainingTagNames = postApplicationService.getPostTags(postId).stream()
                .filter(tag -> !tag.getSlug().equals(slug))
                .map(TagDTO::getName)
                .toList();
        postApplicationService.replacePostTags(userId, postId, remainingTagNames);
    }

    public List<TagDTO> getPostTags(Long postId) {
        return postApplicationService.getPostTags(postId);
    }

}

