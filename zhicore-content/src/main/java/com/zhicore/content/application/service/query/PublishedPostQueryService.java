package com.zhicore.content.application.service.query;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.assembler.PostViewAssembler;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.query.model.CursorToken;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.application.query.model.PostListSort;
import com.zhicore.content.application.service.PostFileUrlResolver;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 已发布文章列表查询服务。
 *
 * 聚合公开文章列表相关的偏移分页、光标分页与混合分页逻辑，
 * 避免这些列表读模型细节继续堆积在更高层的应用服务中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishedPostQueryService {

    private final PostRepository postRepository;
    private final PostFileUrlResolver postFileUrlResolver;

    @Transactional(readOnly = true)
    public List<PostBriefVO> getPublishedPostsCursor(OffsetDateTime cursor, int size) {
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
    public HybridPageResult<PostDTO> getPublishedPostsByAuthor(Long authorId, int page, int size) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<Post> posts = postRepository.findPublishedByAuthor(authorId, offset, safeSize);
        long total = postRepository.countPublishedByAuthor(authorId);
        return HybridPageResult.ofOffset(toClientPostDtos(posts), safePage, safeSize, total);
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
                        vo.setOwnerAvatar(postFileUrlResolver.resolve(post.getOwnerSnapshot().getAvatarId()));
                    } else {
                        vo.setOwnerAvatar(null);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private List<PostDTO> toClientPostDtos(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        return posts.stream()
                .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                .map(post -> {
                    PostDTO dto = new PostDTO();
                    dto.setId(post.getId().getValue());
                    dto.setOwnerId(post.getOwnerId().getValue());
                    dto.setTitle(post.getTitle());
                    dto.setExcerpt(post.getExcerpt());
                    dto.setStatus(post.getStatus().name());
                    dto.setCreatedAt(post.getCreatedAt());
                    dto.setUpdatedAt(post.getUpdatedAt());
                    dto.setPublishedAt(post.getPublishedAt());
                    dto.setCoverImage(post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()
                            ? postFileUrlResolver.resolve(post.getCoverImageId())
                            : null);

                    UserSimpleDTO author = new UserSimpleDTO();
                    author.setId(post.getOwnerId().getValue());
                    author.setUserName(post.getOwnerSnapshot() != null ? post.getOwnerSnapshot().getName() : null);
                    author.setNickname(post.getOwnerSnapshot() != null ? post.getOwnerSnapshot().getName() : null);
                    author.setAvatarId(post.getOwnerSnapshot() != null
                            && post.getOwnerSnapshot().getAvatarId() != null
                            && !post.getOwnerSnapshot().getAvatarId().isEmpty()
                            ? postFileUrlResolver.resolve(post.getOwnerSnapshot().getAvatarId())
                            : null);
                    dto.setAuthor(author);

                    if (post.getStats() != null) {
                        dto.setLikeCount(post.getStats().getLikeCount());
                        dto.setCommentCount(post.getStats().getCommentCount());
                        dto.setFavoriteCount(post.getStats().getFavoriteCount());
                        dto.setViewCount(post.getStats().getViewCount());
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    private PostBriefVO toBriefVO(Post post) {
        PostBriefVO vo = PostViewAssembler.toBriefVO(post);
        if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
            vo.setCoverImageUrl(postFileUrlResolver.resolve(post.getCoverImageId()));
        }
        return vo;
    }
}
