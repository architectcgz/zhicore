package com.zhicore.content.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostFavoriteStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostFavorite;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.repository.PostFavoriteRepository;
import com.zhicore.integration.messaging.post.PostFavoritedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostUnfavoritedIntegrationEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * 文章收藏写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostFavoriteCommandService {

    private final PostFavoriteRepository favoriteRepository;
    private final PostRepository postRepository;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final PostFavoriteStore postFavoriteStore;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    public void favoritePost(Long userId, Long postId) {
        if (Boolean.TRUE.equals(postFavoriteStore.isFavorited(userId, postId))) {
            throw new BusinessException("已经收藏过了");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException("文章未发布，无法收藏");
        }

        Long authorId = post.getOwnerId().getValue();
        transactionTemplate.executeWithoutResult(status -> {
            if (favoriteRepository.exists(postId, userId)) {
                throw new BusinessException("已经收藏过了");
            }

            ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
            if (!idResponse.isSuccess() || idResponse.getData() == null) {
                log.error("Failed to generate favorite ID: {}", idResponse.getMessage());
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成收藏ID失败");
            }
            favoriteRepository.save(new PostFavorite(idResponse.getData(), postId, userId));
        });

        try {
            postFavoriteStore.incrementFavoriteCount(postId);
            postFavoriteStore.markFavorited(userId, postId);
        } catch (Exception e) {
            handleCacheUpdateFailure("favorite", postId, userId, e);
        }

        integrationEventPublisher.publish(new PostFavoritedIntegrationEvent(
                newEventId(),
                java.time.Instant.now(),
                post.getVersion(),
                postId,
                userId,
                authorId
        ));
        log.info("Post favorited: postId={}, userId={}", postId, userId);
    }

    public void unfavoritePost(Long userId, Long postId) {
        if (!Boolean.TRUE.equals(postFavoriteStore.isFavorited(userId, postId)) && !favoriteRepository.exists(postId, userId)) {
            throw new BusinessException("尚未收藏");
        }

        transactionTemplate.executeWithoutResult(status -> favoriteRepository.delete(postId, userId));

        try {
            postFavoriteStore.decrementFavoriteCount(postId);
            postFavoriteStore.unmarkFavorited(userId, postId);
        } catch (Exception e) {
            handleCacheUpdateFailure("unfavorite", postId, userId, e);
        }

        Long aggregateVersion = postRepository.findById(postId)
                .map(Post::getVersion)
                .orElse(0L);
        integrationEventPublisher.publish(new PostUnfavoritedIntegrationEvent(
                newEventId(),
                java.time.Instant.now(),
                aggregateVersion,
                postId,
                userId
        ));
        log.info("Post unfavorited: postId={}, userId={}", postId, userId);
    }

    private void handleCacheUpdateFailure(String operation, Long postId, Long userId, Exception e) {
        log.warn("Redis 更新失败: operation={}, postId={}, userId={}, error={}",
                operation, postId, userId, e.getMessage());
        meterRegistry.counter("cache.update.failure",
                "operation", operation,
                "service", "post-service"
        ).increment();
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
