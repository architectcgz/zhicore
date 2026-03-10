package com.zhicore.ranking.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.PostBatchClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.post.TagDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.ranking.application.sentinel.RankingSentinelHandlers;
import com.zhicore.ranking.application.sentinel.RankingSentinelResources;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 解析文章元数据，用于补齐 ranking 权威状态所需的作者、发布时间和话题信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostMetadataResolver {

    private final PostBatchClient postServiceClient;

    @SentinelResource(
            value = RankingSentinelResources.RESOLVE_POST_METADATA,
            blockHandlerClass = RankingSentinelHandlers.class,
            blockHandler = "handleResolvePostMetadataBlocked"
    )
    public Map<Long, PostMetadata> resolve(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> uniquePostIds = postIds.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (uniquePostIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            ApiResponse<Map<Long, PostDTO>> response = postServiceClient.batchGetPosts(uniquePostIds);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("批量获取文章元数据失败: postIds={}, message={}",
                        uniquePostIds, response != null ? response.getMessage() : "null");
                throw new BusinessException(ResultCode.SERVICE_DEGRADED, "文章服务已降级");
            }

            Map<Long, PostMetadata> result = new LinkedHashMap<>();
            response.getData().forEach((postId, post) -> {
                if (postId != null && post != null) {
                    result.put(postId, toMetadata(post));
                }
            });
            return result;
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            log.error("调用 PostServiceClient 获取文章元数据失败: postIds={}", uniquePostIds, e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "文章服务已降级");
        }
    }

    private PostMetadata toMetadata(PostDTO post) {
        List<Long> topicIds = post.getTags() == null ? Collections.emptyList() : post.getTags().stream()
                .map(TagDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        LocalDateTime publishedAt = post.getPublishedAt() != null ? post.getPublishedAt() : post.getCreatedAt();
        return PostMetadata.builder()
                .postId(post.getId())
                .authorId(post.getOwnerId())
                .publishedAt(publishedAt)
                .topicIds(topicIds)
                .build();
    }

    @Getter
    @Builder
    public static class PostMetadata {

        private Long postId;
        private Long authorId;
        private LocalDateTime publishedAt;
        @Builder.Default
        private List<Long> topicIds = Collections.emptyList();
    }
}
