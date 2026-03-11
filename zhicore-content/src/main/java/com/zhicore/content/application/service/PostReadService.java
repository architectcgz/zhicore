package com.zhicore.content.application.service;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.dto.DraftVO;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostContentVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.domain.model.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章读服务。
 *
 * 负责文章、草稿、标签的查询，不承载写操作。
 */
@Service
@RequiredArgsConstructor
public class PostReadService {

    private final PostApplicationService postApplicationService;

    public PostVO getPost(Long postId) {
        return postApplicationService.getPostById(postId);
    }

    public PostVO getMyPost(Long userId, Long postId) {
        return postApplicationService.getUserPostById(userId, postId);
    }

    public List<PostBriefVO> getMyPosts(Long userId, String status, int page, int size) {
        return postApplicationService.getUserPosts(userId, PostStatus.valueOf(status), page, size);
    }

    public List<PostBriefVO> getPublishedPostsCursor(LocalDateTime cursor, int size) {
        return postApplicationService.getPublishedPostsCursor(cursor, size);
    }

    public HybridPageResult<PostBriefVO> getPublishedPostsHybrid(HybridPageRequest request) {
        return postApplicationService.getPublishedPostsHybrid(request);
    }

    public HybridPageResult<PostBriefVO> getPostList(PostListQuery query) {
        return postApplicationService.getPostList(query);
    }

    public Map<Long, PostDTO> batchGetPosts(Set<Long> postIds) {
        return postApplicationService.batchGetPosts(postIds);
    }

    public PostContentVO getPostContent(Long postId) {
        return postApplicationService.getPostContent(postId);
    }

    public DraftVO getDraft(Long userId, Long postId) {
        return postApplicationService.getDraft(postId, userId);
    }

    public List<DraftVO> getUserDrafts(Long userId) {
        return postApplicationService.getUserDrafts(userId);
    }

    public List<TagDTO> getPostTags(Long postId) {
        return postApplicationService.getPostTags(postId);
    }
}
