package com.zhicore.comment.application.port.store;

import com.zhicore.comment.application.dto.RankingHotPostCandidateMetadata;

import java.util.Optional;
import java.util.Set;

/**
 * ranking 热门文章候选集本地缓存存储。
 */
public interface RankingHotPostCandidateStore {

    boolean contains(Long postId);

    void replaceCandidates(Set<Long> postIds, RankingHotPostCandidateMetadata metadata);

    Optional<RankingHotPostCandidateMetadata> getMetadata();
}
