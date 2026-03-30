package com.zhicore.comment.application.service;

import com.zhicore.comment.application.dto.RankingHotPostCandidateItem;
import com.zhicore.comment.application.dto.RankingHotPostCandidatesResponse;
import com.zhicore.comment.application.dto.RankingHotPostCandidateMetadata;
import com.zhicore.comment.application.port.store.RankingHotPostCandidateStore;
import com.zhicore.comment.infrastructure.feign.RankingServiceClient;
import com.zhicore.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 周期同步 ranking 热门文章候选集。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingHotPostCandidateSyncService {

    private final RankingServiceClient rankingServiceClient;
    private final RankingHotPostCandidateStore rankingHotPostCandidateStore;

    public void refreshCandidates() {
        try {
            ApiResponse<RankingHotPostCandidatesResponse> response = rankingServiceClient.getHotPostCandidates();
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("同步 ranking 热门候选集失败，保留旧值: code={}, message={}",
                        response != null ? response.getCode() : null,
                        response != null ? response.getMessage() : "response is null");
                return;
            }

            List<RankingHotPostCandidateItem> items = response.getData().items();
            Set<Long> candidates = (items == null ? List.<RankingHotPostCandidateItem>of() : items).stream()
                    .map(RankingHotPostCandidateItem::postId)
                    .filter(Objects::nonNull)
                    .map(this::parsePostId)
                    .filter(Objects::nonNull)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);
            rankingHotPostCandidateStore.replaceCandidates(
                    candidates,
                    new RankingHotPostCandidateMetadata(OffsetDateTime.now(), candidates.size())
            );
        } catch (Exception e) {
            log.warn("同步 ranking 热门候选集异常，保留旧值: {}", e.getMessage());
        }
    }

    private Long parsePostId(String postId) {
        try {
            return Long.valueOf(postId);
        } catch (NumberFormatException e) {
            log.warn("忽略非法 ranking 候选文章 ID: {}", postId);
            return null;
        }
    }
}
