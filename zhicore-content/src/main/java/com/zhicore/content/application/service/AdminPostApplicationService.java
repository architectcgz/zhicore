package com.zhicore.content.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.util.QueryParamValidator;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.interfaces.dto.response.PostManageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员文章管理应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPostApplicationService {

    private final PostRepository postRepository;

    /**
     * 查询文章列表
     *
     * @param keyword 关键词
     * @param status 状态（字符串：DRAFT, PUBLISHED, SCHEDULED, DELETED）
     * @param authorId 作者ID
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    @Transactional(readOnly = true)
    public PageResult<PostManageDTO> queryPosts(String keyword, String status, Long authorId, int page, int size) {
        try {
            // 参数验证和规范化
            keyword = QueryParamValidator.validateKeyword(keyword);
            status = QueryParamValidator.validateStatus(status);
            
            // 将状态字符串转换为数字代码（数据库存储的是 Integer）
            String statusCode = null;
            if (status != null) {
                try {
                    PostStatus postStatus = PostStatus.valueOf(status);
                    statusCode = String.valueOf(postStatus.getCode());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status value: {}", status);
                    // 无效状态，忽略该筛选条件
                }
            }
            
            String authorIdStr = authorId != null ? String.valueOf(authorId) : null;
            authorIdStr = QueryParamValidator.validateId(authorIdStr);
            page = QueryParamValidator.validatePage(page);
            size = QueryParamValidator.validateSize(size);

            // 计算偏移量
            int offset = QueryParamValidator.calculateOffset(page, size);

            // 执行数据库查询（使用状态代码）
            List<Post> posts = postRepository.findByConditions(keyword, statusCode, authorId, offset, size);
            long total = postRepository.countByConditions(keyword, statusCode, authorId);

            // 转换为 DTO
            List<PostManageDTO> dtoList = posts.stream()
                    .map(this::convertToManageDTO)
                    .collect(Collectors.toList());

            log.debug("Query posts: keyword={}, status={}, statusCode={}, authorId={}, page={}, size={}, total={}",
                    keyword, status, statusCode, authorId, page, size, total);

            return PageResult.of(page, size, total, dtoList);

        } catch (Exception e) {
            log.error("Failed to query posts: keyword={}, status={}, authorId={}, page={}, size={}",
                    keyword, status, authorId, page, size, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "查询文章列表失败");
        }
    }

    /**
     * 删除文章（软删除）
     *
     * @param postId 文章ID
     */
    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.POST_NOT_FOUND));

        post.delete();
        postRepository.update(post);

        log.info("Admin deleted post: postId={}", postId);
    }

    /**
     * 转换为管理 DTO
     */
    private PostManageDTO convertToManageDTO(Post post) {
        return PostManageDTO.builder()
                .id(post.getId().getValue())
                .title(post.getTitle())
                .authorId(post.getOwnerId().getValue())
                .authorName("") // 需要从用户服务获取，这里简化处理
                .status(post.getStatus().name())
                .viewCount((int) post.getStats().getViewCount())
                .likeCount(post.getStats().getLikeCount())
                .commentCount(post.getStats().getCommentCount())
                .createdAt(post.getCreatedAt())
                .publishedAt(post.getPublishedAt())
                .build();
    }
}
