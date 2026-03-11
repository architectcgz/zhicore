package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.util.QueryParamValidator;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端文章查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPostQueryService {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.ADMIN_QUERY_POSTS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleAdminQueryPostsBlocked"
    )
    public PageResult<PostManageDTO> queryPosts(String keyword, String status, Long authorId, int page, int size) {
        try {
            keyword = QueryParamValidator.validateKeyword(keyword);
            status = QueryParamValidator.validateStatus(status);

            String statusCode = null;
            if (status != null) {
                try {
                    PostStatus postStatus = PostStatus.valueOf(status);
                    statusCode = String.valueOf(postStatus.getCode());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status value: {}", status);
                }
            }

            String authorIdStr = authorId != null ? String.valueOf(authorId) : null;
            QueryParamValidator.validateId(authorIdStr);
            page = QueryParamValidator.validatePage(page);
            size = QueryParamValidator.validateSize(size);

            int offset = QueryParamValidator.calculateOffset(page, size);
            List<Post> posts = postRepository.findByConditions(keyword, statusCode, authorId, offset, size);
            long total = postRepository.countByConditions(keyword, statusCode, authorId);

            List<PostManageDTO> dtoList = posts.stream()
                    .map(this::convertToManageDTO)
                    .toList();

            log.debug("Query posts: keyword={}, status={}, statusCode={}, authorId={}, page={}, size={}, total={}",
                    keyword, status, statusCode, authorId, page, size, total);
            return PageResult.of(page, size, total, dtoList);
        } catch (Exception e) {
            log.error("Failed to query posts: keyword={}, status={}, authorId={}, page={}, size={}",
                    keyword, status, authorId, page, size, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "查询文章列表失败");
        }
    }

    private PostManageDTO convertToManageDTO(Post post) {
        return PostManageDTO.builder()
                .id(post.getId().getValue())
                .title(post.getTitle())
                .authorId(post.getOwnerId().getValue())
                .authorName("")
                .status(post.getStatus().name())
                .viewCount((int) post.getStats().getViewCount())
                .likeCount(post.getStats().getLikeCount())
                .commentCount(post.getStats().getCommentCount())
                .createdAt(post.getCreatedAt())
                .publishedAt(post.getPublishedAt())
                .build();
    }
}
