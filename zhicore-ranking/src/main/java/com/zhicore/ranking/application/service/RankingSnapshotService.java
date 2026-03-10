package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.SnapshotInboxEvent;
import com.zhicore.ranking.application.model.SnapshotPostHotState;
import com.zhicore.ranking.application.port.policy.RankingSnapshotPolicy;
import com.zhicore.ranking.application.port.store.RankingSnapshotCacheStore;
import com.zhicore.ranking.application.port.store.RankingSnapshotSourceStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从权威状态和 DONE inbox 事件刷新 Redis 排行快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingSnapshotService {

    private final RankingSnapshotSourceStore snapshotSourceStore;
    private final RankingSnapshotCacheStore snapshotCacheStore;
    private final RankingSnapshotPolicy snapshotPolicy;
    private final HotScoreCalculator hotScoreCalculator;

    public void refreshCurrentSnapshots() {
        List<SnapshotPostHotState> states = snapshotSourceStore.listActivePostStates();
        Map<Long, SnapshotPostHotState> stateMap = new HashMap<>();
        states.forEach(state -> stateMap.put(state.getPostId(), state));

        List<HotScore> totalPostRanking = buildPostScores(states);
        List<HotScore> totalCreatorRanking = buildCreatorScores(totalPostRanking, stateMap);
        List<HotScore> totalTopicRanking = buildTopicScores(totalPostRanking, stateMap);
        snapshotCacheStore.replaceTotalRanking(totalPostRanking, totalCreatorRanking, totalTopicRanking);

        LocalDate today = LocalDate.now();
        PeriodSnapshot dailySnapshot = buildPeriodSnapshot(
                snapshotSourceStore.listDoneEventsBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                stateMap
        );
        snapshotCacheStore.replaceDailyRanking(
                today,
                dailySnapshot.postScores(),
                dailySnapshot.creatorScores(),
                dailySnapshot.topicScores()
        );

        LocalDate weekStartDate = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        PeriodSnapshot weeklySnapshot = buildPeriodSnapshot(
                snapshotSourceStore.listDoneEventsBetween(weekStartDate.atStartOfDay(), weekStartDate.plusWeeks(1).atStartOfDay()),
                stateMap
        );
        snapshotCacheStore.replaceWeeklyPostRanking(weeklySnapshot.postScores());

        LocalDate monthStartDate = today.withDayOfMonth(1);
        PeriodSnapshot monthlySnapshot = buildPeriodSnapshot(
                snapshotSourceStore.listDoneEventsBetween(monthStartDate.atStartOfDay(), monthStartDate.plusMonths(1).atStartOfDay()),
                stateMap
        );
        snapshotCacheStore.replaceMonthlyPostRanking(monthlySnapshot.postScores());
    }

    private List<HotScore> buildPostScores(Collection<SnapshotPostHotState> states) {
        List<HotScore> scores = new ArrayList<>();
        for (SnapshotPostHotState state : states) {
            double score = hotScoreCalculator.calculatePostHotScore(toPostStats(state), state.getPublishedAt());
            if (score > 0) {
                scores.add(HotScore.of(String.valueOf(state.getPostId()), score));
            }
        }
        return sortAndTrim(scores);
    }

    private List<HotScore> buildCreatorScores(List<HotScore> postScores, Map<Long, SnapshotPostHotState> stateMap) {
        Map<String, Double> creatorScores = new HashMap<>();
        for (HotScore score : postScores) {
            SnapshotPostHotState state = stateMap.get(Long.parseLong(score.getEntityId()));
            if (state == null || state.getAuthorId() == null) {
                continue;
            }
            creatorScores.merge(String.valueOf(state.getAuthorId()), score.getScore(), Double::sum);
        }
        return toHotScores(creatorScores);
    }

    private List<HotScore> buildTopicScores(List<HotScore> postScores, Map<Long, SnapshotPostHotState> stateMap) {
        Map<String, Double> topicScores = new HashMap<>();
        for (HotScore score : postScores) {
            SnapshotPostHotState state = stateMap.get(Long.parseLong(score.getEntityId()));
            if (state == null || state.getTopicIds() == null || state.getTopicIds().isEmpty()) {
                continue;
            }
            for (Long topicId : state.getTopicIds()) {
                if (topicId != null) {
                    topicScores.merge(String.valueOf(topicId), score.getScore(), Double::sum);
                }
            }
        }
        return toHotScores(topicScores);
    }

    private PeriodSnapshot buildPeriodSnapshot(List<SnapshotInboxEvent> periodEvents,
                                               Map<Long, SnapshotPostHotState> stateMap) {
        if (periodEvents == null || periodEvents.isEmpty()) {
            return new PeriodSnapshot(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        Map<Long, MetricCounter> counters = new LinkedHashMap<>();
        for (SnapshotInboxEvent event : periodEvents) {
            if (event.getPostId() == null || event.getMetricType() == null) {
                continue;
            }
            MetricCounter counter = counters.computeIfAbsent(event.getPostId(), ignored -> new MetricCounter());
            counter.add(event.getMetricType(), event.getCountDelta());
        }

        List<HotScore> postScores = new ArrayList<>();
        for (Map.Entry<Long, MetricCounter> entry : counters.entrySet()) {
            SnapshotPostHotState state = stateMap.get(entry.getKey());
            if (state == null) {
                continue;
            }
            MetricCounter counter = entry.getValue();
            PostStats stats = PostStats.builder()
                    .viewCount(Math.max(0, counter.viewCount))
                    .likeCount(Math.max(0, counter.likeCount))
                    .commentCount(Math.max(0, counter.commentCount))
                    .favoriteCount(Math.max(0, counter.favoriteCount))
                    .build();
            double score = hotScoreCalculator.calculatePostHotScore(stats, state.getPublishedAt());
            if (score > 0) {
                postScores.add(HotScore.of(String.valueOf(entry.getKey()), score));
            }
        }

        List<HotScore> trimmedPostScores = sortAndTrim(postScores);
        return new PeriodSnapshot(
                trimmedPostScores,
                buildCreatorScores(trimmedPostScores, stateMap),
                buildTopicScores(trimmedPostScores, stateMap)
        );
    }

    private List<HotScore> toHotScores(Map<String, Double> scoreMap) {
        List<HotScore> scores = scoreMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> HotScore.of(entry.getKey(), entry.getValue()))
                .toList();
        return sortAndTrim(scores);
    }

    private List<HotScore> sortAndTrim(List<HotScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(HotScore::getScore).reversed()
                        .thenComparing(HotScore::getEntityId))
                .limit(snapshotPolicy.topSize())
                .toList();
    }

    private PostStats toPostStats(SnapshotPostHotState state) {
        return PostStats.builder()
                .viewCount(state.getViewCount())
                .likeCount(state.getLikeCount())
                .commentCount(state.getCommentCount())
                .favoriteCount(state.getFavoriteCount())
                .build();
    }

    private record PeriodSnapshot(List<HotScore> postScores,
                                  List<HotScore> creatorScores,
                                  List<HotScore> topicScores) {
    }

    private static class MetricCounter {

        private long viewCount;
        private int likeCount;
        private int commentCount;
        private int favoriteCount;

        private void add(RankingMetricType metricType, int delta) {
            switch (metricType) {
                case VIEW -> viewCount += delta;
                case LIKE -> likeCount += delta;
                case COMMENT -> commentCount += delta;
                case FAVORITE -> favoriteCount += delta;
            }
        }
    }
}
