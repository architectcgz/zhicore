package com.zhicore.comment.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.comment.application.dto.RankingHotPostCandidateMetadata;
import com.zhicore.comment.application.port.store.RankingHotPostCandidateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 基于 Redis 的 ranking 热门文章候选集本地存储。
 */
@Component
@RequiredArgsConstructor
public class RedisRankingHotPostCandidateStore implements RankingHotPostCandidateStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean contains(Long postId) {
        if (postId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(
                CommentRedisKeys.rankingHotPostCandidates(),
                String.valueOf(postId)
        ));
    }

    @Override
    public void replaceCandidates(Set<Long> postIds, RankingHotPostCandidateMetadata metadata) {
        String setKey = CommentRedisKeys.rankingHotPostCandidates();
        redisTemplate.delete(setKey);
        if (postIds != null && !postIds.isEmpty()) {
            String[] members = postIds.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForSet().add(setKey, (Object[]) members);
        }
        redisTemplate.opsForValue().set(CommentRedisKeys.rankingHotPostCandidatesMeta(), metadata);
    }

    @Override
    public Optional<RankingHotPostCandidateMetadata> getMetadata() {
        Object cached = redisTemplate.opsForValue().get(CommentRedisKeys.rankingHotPostCandidatesMeta());
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.convertValue(cached, RankingHotPostCandidateMetadata.class));
    }
}
