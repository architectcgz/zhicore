package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.dto.HotPostCandidateItemDTO;
import com.zhicore.ranking.application.dto.HotPostCandidatesDTO;
import com.zhicore.ranking.application.model.HotPostCandidateMeta;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.config.RankingCandidateProperties;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 热门文章候选集服务。
 */
@Service
@RequiredArgsConstructor
public class RankingHotPostCandidateService {

    private static final DateTimeFormatter VERSION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingCandidateProperties candidateProperties;

    /**
     * 从当前总榜快照刷新候选集。
     */
    public void refreshCandidates() {
        if (!candidateProperties.getPosts().isEnabled()) {
            return;
        }

        int configuredSize = candidateProperties.getPosts().getSize();
        List<HotScore> candidates = rankingRedisRepository.getTopRanking(
                        RankingRedisKeys.hotPosts(),
                        0,
                        configuredSize - 1
                ).stream()
                .filter(score -> score.getScore() > 0D)
                .toList();
        Instant generatedAt = Instant.now();
        long sourceCount = rankingRedisRepository.getSortedSetSize(RankingRedisKeys.hotPosts());

        rankingRedisRepository.replaceHotPostCandidates(candidates, HotPostCandidateMeta.builder()
                .version(VERSION_FORMATTER.format(generatedAt))
                .generatedAt(generatedAt)
                .candidateSize(candidates.size())
                .sourceKey(RankingRedisKeys.hotPosts())
                .sourceCount(sourceCount)
                .minScore(candidates.isEmpty() ? 0D : candidates.get(candidates.size() - 1).getScore())
                .stale(false)
                .build());
    }

    /**
     * 查询热门候选集。
     */
    public HotPostCandidatesDTO getCandidates() {
        HotPostCandidateMeta meta = rankingRedisRepository.getHotPostCandidateMeta();
        if (meta == null) {
            return HotPostCandidatesDTO.builder()
                    .candidateSize(0)
                    .stale(true)
                    .items(List.of())
                    .build();
        }

        boolean stale = isStale(meta.getGeneratedAt()) || meta.isStale();
        if (meta.isStale() != stale) {
            rankingRedisRepository.updateHotPostCandidateStale(stale);
            meta = meta.toBuilder().stale(stale).build();
        }

        List<HotPostCandidateItemDTO> items = rankingRedisRepository.getHotPostCandidates(meta.getCandidateSize()).stream()
                .map(score -> HotPostCandidateItemDTO.builder()
                        .postId(score.getEntityId())
                        .rank(score.getRank())
                        .score(score.getScore())
                        .build())
                .toList();

        return HotPostCandidatesDTO.builder()
                .version(meta.getVersion())
                .generatedAt(meta.getGeneratedAt())
                .candidateSize(meta.getCandidateSize())
                .stale(meta.isStale())
                .items(items)
                .build();
    }

    private boolean isStale(Instant generatedAt) {
        if (generatedAt == null) {
            return true;
        }
        Duration age = Duration.between(generatedAt, Instant.now());
        return age.toMillis() > candidateProperties.getPosts().getStaleThreshold();
    }
}
