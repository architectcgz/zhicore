package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.assembler.PostViewAssembler;
import com.zhicore.content.application.dto.DraftVO;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostContentVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.application.query.PostQuery;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.service.query.MyPostQueryService;
import com.zhicore.content.application.service.query.PostBatchQueryService;
import com.zhicore.content.application.service.query.PostTagQueryService;
import com.zhicore.content.application.service.query.PublishedPostQueryService;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.service.DraftQueryService;
import com.zhicore.content.domain.valueobject.DraftSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章查询门面。
 *
 * 负责文章、草稿、标签的查询入口编排，不承载写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostQueryFacade {

    private final OwnedPostLoadService ownedPostLoadService;
    private final PostQuery postQuery;
    private final PostContentStore postContentStore;
    private final PublishedPostQueryService publishedPostQueryService;
    private final MyPostQueryService myPostQueryService;
    private final PostBatchQueryService postBatchQueryService;
    private final PostFileUrlResolver postFileUrlResolver;
    private final DraftQueryService draftQueryService;
    private final PostTagQueryService postTagQueryService;

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_POST_DETAIL,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostDetailBlocked"
    )
    public PostVO getPost(Long postId) {
        PostDetailView detailView = postQuery.getDetail(PostId.of(postId));
        if (detailView == null || detailView.getStatus() == PostStatus.DELETED) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return toPostVO(detailView);
    }

    @Transactional(readOnly = true)
    public PostVO getMyPost(Long userId, Long postId) {
        PostDetailView detailView = postQuery.getDetail(PostId.of(postId));
        if (detailView == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        if (!detailView.getOwnerSnapshot().getOwnerId().getValue().equals(userId)) {
            throw new ForbiddenException("无权访问此文章");
        }
        if (detailView.getStatus() == PostStatus.DELETED) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章已删除");
        }
        return toPostVO(detailView);
    }

    @Transactional(readOnly = true)
    public List<PostBriefVO> getMyPosts(Long userId, String status, int page, int size) {
        return myPostQueryService.getMyPosts(userId, status, page, size);
    }

    @Transactional(readOnly = true)
    public List<PostBriefVO> getPublishedPostsCursor(OffsetDateTime cursor, int size) {
        return publishedPostQueryService.getPublishedPostsCursor(cursor, size);
    }

    @Transactional(readOnly = true)
    public HybridPageResult<PostBriefVO> getPublishedPostsHybrid(HybridPageRequest request) {
        return publishedPostQueryService.getPublishedPostsHybrid(request);
    }

    @Transactional(readOnly = true)
    public HybridPageResult<PostBriefVO> getPostList(PostListQuery query) {
        return publishedPostQueryService.getPostList(query);
    }

    @Transactional(readOnly = true)
    public HybridPageResult<PostDTO> getPublishedPostsByAuthor(Long authorId, int page, int size) {
        return publishedPostQueryService.getPublishedPostsByAuthor(authorId, page, size);
    }

    @Transactional(readOnly = true)
    public Map<Long, PostDTO> batchGetPosts(Set<Long> postIds) {
        return postBatchQueryService.batchGetPosts(postIds);
    }

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_POST_CONTENT,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostContentBlocked"
    )
    public PostContentVO getPostContent(Long postId) {
        Optional<PostBody> bodyOpt = postContentStore.getContent(PostId.of(postId));
        if (bodyOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章内容不存在");
        }

        PostBody body = bodyOpt.get();
        PostContentVO content = new PostContentVO();
        content.setPostId(String.valueOf(postId));
        content.setContentType(body.getContentType().getValue());
        content.setRaw(body.getContent());
        content.setCreatedAt(body.getCreatedAt());
        content.setUpdatedAt(body.getUpdatedAt());
        return content;
    }

    @Transactional(readOnly = true)
    public DraftVO getDraft(Long userId, Long postId) {
        ownedPostLoadService.load(postId, userId);
        DraftSnapshot draft = draftQueryService.getLatestDraft(postId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "草稿不存在"));
        return toDraftVO(draft);
    }

    @Transactional(readOnly = true)
    public List<DraftVO> getUserDrafts(Long userId) {
        return draftQueryService.getUserDrafts(userId).stream()
                .map(this::toDraftVO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TagDTO> getPostTags(Long postId) {
        return postTagQueryService.getPostTags(postId);
    }

    private PostVO toPostVO(PostDetailView detailView) {
        PostVO vo = new PostVO();
        vo.setId(detailView.getId().getValue());
        vo.setOwnerId(detailView.getOwnerSnapshot().getOwnerId().getValue());
        vo.setOwnerName(detailView.getOwnerSnapshot().getName());
        vo.setTitle(detailView.getTitle());
        vo.setRaw(detailView.getContent());
        vo.setExcerpt(detailView.getExcerpt());
        vo.setStatus(detailView.getStatus().name());
        vo.setPublishedAt(detailView.getPublishedAt());
        vo.setScheduledAt(detailView.getScheduledPublishAt());
        vo.setCreatedAt(detailView.getCreatedAt());
        vo.setUpdatedAt(detailView.getUpdatedAt());
        vo.setViewCount(detailView.getViewCount());
        vo.setLikeCount((int) detailView.getLikeCount());
        vo.setCommentCount((int) detailView.getCommentCount());
        vo.setFavoriteCount(detailView.getFavoriteCount());

        if (detailView.getCoverImage() != null && !detailView.getCoverImage().isEmpty()) {
            vo.setCoverImageUrl(postFileUrlResolver.resolve(detailView.getCoverImage()));
        }
        if (detailView.getOwnerSnapshot().getAvatarId() != null
                && !detailView.getOwnerSnapshot().getAvatarId().isEmpty()) {
            vo.setOwnerAvatar(postFileUrlResolver.resolve(detailView.getOwnerSnapshot().getAvatarId()));
        }
        return vo;
    }

    private DraftVO toDraftVO(DraftSnapshot draft) {
        return DraftVO.builder()
                .id(draft.getId())
                .postId(draft.getPostId())
                .userId(draft.getUserId())
                .content(draft.getContent())
                .contentType(draft.getContentType())
                .savedAt(draft.getSavedAt())
                .deviceId(draft.getDeviceId())
                .isAutoSave(draft.getIsAutoSave())
                .wordCount(draft.getWordCount())
                .build();
    }

}
