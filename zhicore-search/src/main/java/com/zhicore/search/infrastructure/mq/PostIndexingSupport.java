package com.zhicore.search.infrastructure.mq;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.search.domain.model.PostDocument;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 搜索索引同步辅助方法。
 *
 * 将远程文章查询和文档映射规则收口，避免消费者各自处理重试与缺失判断。
 */
final class PostIndexingSupport {

    private PostIndexingSupport() {
    }

    static Optional<PostDetailDTO> loadPostDetail(PostServiceClient postServiceClient, Long postId, String action) {
        ApiResponse<PostDetailDTO> response = postServiceClient.getPostById(postId);
        if (response == null) {
            throw new IllegalStateException("文章服务返回空响应，无法" + action + "搜索索引: postId=" + postId);
        }

        if (response.isSuccess()) {
            if (response.getData() == null) {
                throw new IllegalStateException("文章服务返回空数据，无法" + action + "搜索索引: postId=" + postId);
            }
            return Optional.of(response.getData());
        }

        if (isPostMissing(response)) {
            return Optional.empty();
        }

        throw new IllegalStateException(
            "文章服务调用失败，无法" + action + "搜索索引: postId=" + postId + ", code=" + response.getCode()
                + ", message=" + response.getMessage()
        );
    }

    static PostDocument toDocument(PostDetailDTO post) {
        List<PostDocument.TagInfo> tagInfos = null;
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            tagInfos = post.getTags().stream()
                .map(tag -> PostDocument.TagInfo.builder()
                    .id(String.valueOf(tag.getId()))
                    .name(tag.getName())
                    .slug(tag.getSlug())
                    .build())
                .collect(Collectors.toList());
        }

        return PostDocument.builder()
            .id(String.valueOf(post.getId()))
            .title(post.getTitle())
            .content(post.getRaw())
            .excerpt(post.getExcerpt())
            .authorId(post.getAuthor() != null ? String.valueOf(post.getAuthor().getId()) : null)
            .authorName(post.getAuthor() != null ? post.getAuthor().getNickname() : null)
            .tags(tagInfos)
            .categoryName(post.getCategories() != null && !post.getCategories().isEmpty()
                ? post.getCategories().get(0) : null)
            .status("PUBLISHED")
            .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0)
            .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0)
            .viewCount(post.getViewCount() != null ? post.getViewCount().longValue() : 0L)
            .publishedAt(post.getPublishedAt())
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt())
            .build();
    }

    private static boolean isPostMissing(ApiResponse<?> response) {
        return response.getCode() == ResultCode.NOT_FOUND.getCode()
            || response.getCode() == ResultCode.POST_NOT_FOUND.getCode()
            || response.getCode() == ResultCode.DATA_NOT_FOUND.getCode();
    }
}
