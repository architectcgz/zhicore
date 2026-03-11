package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.UploadFileClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.assembler.PostViewAssembler;
import com.zhicore.content.application.dto.DraftVO;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostContentVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.application.query.PostQuery;
import com.zhicore.content.application.query.model.CursorToken;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.application.query.model.PostListSort;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.domain.service.DraftService;
import com.zhicore.content.domain.valueobject.DraftSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章读服务。
 *
 * 负责文章、草稿、标签的查询，不承载写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostReadService {

    private final PostRepository postRepository;
    private final PostQuery cacheAsidePostQuery;
    private final PostContentStore postContentStore;
    private final DraftService draftService;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final UploadFileClient uploadServiceClient;

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_POST_DETAIL,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostDetailBlocked"
    )
    public PostVO getPost(Long postId) {
        PostDetailView detailView = cacheAsidePostQuery.getDetail(PostId.of(postId));
        if (detailView == null || detailView.getStatus() == PostStatus.DELETED) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }
        return toPostVO(detailView);
    }

    @Transactional(readOnly = true)
    public PostVO getMyPost(Long userId, Long postId) {
        PostDetailView detailView = cacheAsidePostQuery.getDetail(PostId.of(postId));
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
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findByOwnerId(userId, PostStatus.valueOf(status), offset, size);
        return posts.stream()
                .map(this::toBriefVO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PostBriefVO> getPublishedPostsCursor(LocalDateTime cursor, int size) {
        return postRepository.findPublishedCursor(cursor, size).stream()
                .map(this::toBriefVO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HybridPageResult<PostBriefVO> getPublishedPostsHybrid(HybridPageRequest request) {
        int size = Math.min(request.getSize(), 100);
        if (request.isCursorMode()) {
            return queryPublishedPostsCursor(request.getCursor(), size);
        }

        int page = request.getPage() != null ? request.getPage() : 1;
        if (page < 1) {
            page = 1;
        }
        if (request.exceedsHybridThreshold()) {
            return queryPublishedPostsOffsetWithSuggestion(page, size);
        }
        return queryPublishedPostsOffset(page, size);
    }

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.GET_POST_LIST,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostListBlocked"
    )
    public HybridPageResult<PostBriefVO> getPostList(PostListQuery query) {
        PostListQuery safeQuery = query != null ? query : PostListQuery.builder().build();
        safeQuery.validate();

        PostListSort sort = safeQuery.normalizedSort();
        int size = safeQuery.normalizedSize();
        if (sort == PostListSort.LATEST) {
            return queryPublishedPostsCursor(safeQuery.getCursor(), size);
        }

        int page = safeQuery.normalizedPage();
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findPublishedPopular(offset, size);
        long total = postRepository.countPublished();
        return HybridPageResult.ofOffset(enrichPostsWithAuthorInfo(posts), page, size, total);
    }

    @Transactional(readOnly = true)
    public Map<Long, PostDTO> batchGetPosts(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Post> posts = postRepository.findByIds(new ArrayList<>(postIds));
        Map<Long, PostDTO> result = new HashMap<>();
        for (Map.Entry<Long, Post> entry : posts.entrySet()) {
            Post post = entry.getValue();
            if (!post.isDeleted()) {
                PostDTO dto = new PostDTO();
                dto.setId(post.getId().getValue());
                dto.setTitle(post.getTitle());
                dto.setOwnerId(post.getOwnerId().getValue());
                dto.setStatus(post.getStatus().name());
                dto.setCreatedAt(post.getCreatedAt());
                dto.setPublishedAt(post.getPublishedAt());
                result.put(post.getId().getValue(), dto);
            }
        }
        return result;
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
        getPostAndCheckOwnership(postId, userId);
        DraftSnapshot draft = draftService.getLatestDraft(postId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "草稿不存在"));
        return toDraftVO(draft);
    }

    @Transactional(readOnly = true)
    public List<DraftVO> getUserDrafts(Long userId) {
        return draftService.getUserDrafts(userId).stream()
                .map(this::toDraftVO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TagDTO> getPostTags(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
        if (post.isDeleted()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章已删除");
        }

        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tagRepository.findByIdIn(tagIds).stream()
                .map(tag -> TagDTO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .description(tag.getDescription())
                        .createdAt(tag.getCreatedAt())
                        .updatedAt(tag.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private HybridPageResult<PostBriefVO> queryPublishedPostsOffset(int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findPublished(offset, size);
        long total = postRepository.countPublished();
        return HybridPageResult.ofOffset(enrichPostsWithAuthorInfo(posts), page, size, total);
    }

    private HybridPageResult<PostBriefVO> queryPublishedPostsOffsetWithSuggestion(int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findPublished(offset, size);
        long total = postRepository.countPublished();
        return HybridPageResult.ofOffsetWithSuggestion(enrichPostsWithAuthorInfo(posts), page, size, total);
    }

    private HybridPageResult<PostBriefVO> queryPublishedPostsCursor(String cursor, int size) {
        CursorToken token = CursorToken.decodeOrThrow(cursor);
        List<Post> posts = postRepository.findPublishedCursor(
                token != null ? token.getPublishedAt() : null,
                token != null ? token.getPostId() : null,
                size + 1
        );

        boolean hasMore = posts.size() > size;
        if (hasMore) {
            posts = posts.subList(0, size);
        }

        String nextCursor = null;
        if (!posts.isEmpty()) {
            Post lastPost = posts.get(posts.size() - 1);
            nextCursor = CursorToken.encode(new CursorToken(lastPost.getPublishedAt(), lastPost.getId().getValue()));
        }
        return HybridPageResult.ofCursor(enrichPostsWithAuthorInfo(posts), nextCursor, hasMore, size);
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

        if (detailView.getCoverImage() != null && !detailView.getCoverImage().isEmpty()) {
            vo.setCoverImageUrl(getFileUrl(detailView.getCoverImage()));
        }
        if (detailView.getOwnerSnapshot().getAvatarId() != null
                && !detailView.getOwnerSnapshot().getAvatarId().isEmpty()) {
            vo.setOwnerAvatar(getFileUrl(detailView.getOwnerSnapshot().getAvatarId()));
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

    private PostBriefVO toBriefVO(Post post) {
        PostBriefVO vo = PostViewAssembler.toBriefVO(post);
        if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
            vo.setCoverImageUrl(getFileUrl(post.getCoverImageId()));
        }
        return vo;
    }

    private Post getPostAndCheckOwnership(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
        if (!post.isOwnedBy(UserId.of(userId))) {
            throw new ForbiddenException("无权操作此文章");
        }
        return post;
    }

    private String getFileUrl(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return null;
        }

        try {
            ApiResponse<String> response = uploadServiceClient.getFileUrl(fileId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
            log.warn("Failed to get file URL from upload service: fileId={}, response={}", fileId, response);
        } catch (Exception e) {
            log.error("Failed to get file URL: fileId={}", fileId, e);
        }
        return null;
    }

    private List<PostBriefVO> enrichPostsWithAuthorInfo(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("使用缓存的作者信息填充 {} 篇文章", posts.size());
        return posts.stream()
                .map(post -> {
                    PostBriefVO vo = toBriefVO(post);
                    vo.setOwnerName(post.getOwnerSnapshot().getName());
                    if (post.getOwnerSnapshot().getAvatarId() != null
                            && !post.getOwnerSnapshot().getAvatarId().isEmpty()) {
                        vo.setOwnerAvatar(getFileUrl(post.getOwnerSnapshot().getAvatarId()));
                    } else {
                        vo.setOwnerAvatar(null);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
