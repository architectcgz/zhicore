package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingSnapshotProperties;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInbox;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInboxRepository;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotState;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotStateRepository;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final Duration DAILY_TTL = Duration.ofDays(2);
    private static final Duration WEEKLY_TTL = Duration.ofDays(14);
    private static final Duration MONTHLY_TTL = Duration.ofDays(365);

    private final RankingPostHotStateRepository postHotStateRepository;
    private final RankingEventInboxRepository inboxRepository;
    private final RankingRedisRepository rankingRedisRepository;
    private final RankingSnapshotProperties snapshotProperties;
    private final HotScoreCalculator hotScoreCalculator;

    public void refreshCurrentSnapshots() {
        List<RankingPostHotState> states = postHotStateRepository.findByStatus(ACTIVE_STATUS);
        Map<Long, RankingPostHotState> stateMap = new HashMap<>();
        states.forEach(state -> stateMap.put(state.getPostId(), state));

        List<HotScore> totalPostRanking = buildPostScores(states);
        List<HotScore> totalCreatorRanking = buildCreatorScores(totalPostRanking, stateMap);
        List<HotScore> totalTopicRanking = buildTopicScores(totalPostRanking, stateMap);

        rankingRedisRepository.replaceRanking(RankingRedisKeys.hotPosts(), totalPostRanking, null);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.hotCreators(), totalCreatorRanking, null);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.hotTopics(), totalTopicRanking, null);

        refreshPeriodSnapshot(LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay(),
                RankingRedisKeys.todayPosts(),
                DAILY_TTL,
                RankingRedisKeys.dailyCreators(LocalDate.now()),
                RankingRedisKeys.dailyTopics(LocalDate.now()),
                stateMap);

        LocalDate weekStartDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        refreshPeriodSnapshot(weekStartDate.atStartOfDay(),
                weekStartDate.plusWeeks(1).atStartOfDay(),
                RankingRedisKeys.currentWeekPosts(),
                WEEKLY_TTL,
                null,
                null,
                stateMap);

        LocalDate monthStartDate = LocalDate.now().withDayOfMonth(1);
        refreshPeriodSnapshot(monthStartDate.atStartOfDay(),
                monthStartDate.plusMonths(1).atStartOfDay(),
                RankingRedisKeys.currentMonthPosts(),
                MONTHLY_TTL,
                null,
                null,
                stateMap);
    }

    private void refreshPeriodSnapshot(LocalDateTime start,
                                       LocalDateTime end,
                                       String postKey,
                                       Duration ttl,
                                       String creatorKey,
                                       String topicKey,
                                       Map<Long, RankingPostHotState> stateMap) {
        List<RankingEventInbox> periodEvents = inboxRepository.findByStatusAndOccurredAtBetweenOrderByOccurredAtAsc(
                RankingEventInbox.InboxStatus.DONE,
                start,
                end
        );
        PeriodSnapshot periodSnapshot = buildPeriodSnapshot(periodEvents, stateMap);
        rankingRedisRepository.replaceRanking(postKey, periodSnapshot.postScores(), ttl);
        if (creatorKey != null) {
            rankingRedisRepository.replaceRanking(creatorKey, periodSnapshot.creatorScores(), ttl);
        }
        if (topicKey != null) {
            rankingRedisRepository.replaceRanking(topicKey, periodSnapshot.topicScores(), ttl);
        }
    }

    private List<HotScore> buildPostScores(Collection<RankingPostHotState> states) {
        List<HotScore> scores = new ArrayList<>();
        for (RankingPostHotState state : states) {
            double score = hotScoreCalculator.calculatePostHotScore(toPostStats(state), state.getPublishedAt());
            if (score > 0) {
                scores.add(HotScore.of(String.valueOf(state.getPostId()), score));
            }
        }
        return sortAndTrim(scores);
    }

    private List<HotScore> buildCreatorScores(List<HotScore> postScores, Map<Long, RankingPostHotState> stateMap) {
        Map<String, Double> creatorScores = new HashMap<>();
        for (HotScore score : postScores) {
            RankingPostHotState state = stateMap.get(Long.parseLong(score.getEntityId()));
            if (state == null || state.getAuthorId() == null) {
                continue;
            }
            creatorScores.merge(String.valueOf(state.getAuthorId()), score.getScore(), Double::sum);
        }
        return toHotScores(creatorScores);
    }

    private List<HotScore> buildTopicScores(List<HotScore> postScores, Map<Long, RankingPostHotState> stateMap) {
        Map<String, Double> topicScores = new HashMap<>();
        for (HotScore score : postScores) {
            RankingPostHotState state = stateMap.get(Long.parseLong(score.getEntityId()));
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

    private PeriodSnapshot buildPeriodSnapshot(List<RankingEventInbox> periodEvents,
                                               Map<Long, RankingPostHotState> stateMap) {
        if (periodEvents == null || periodEvents.isEmpty()) {
            return new PeriodSnapshot(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        Map<Long, MetricCounter> counters = new LinkedHashMap<>();
        for (RankingEventInbox event : periodEvents) {
            if (event.getPostId() == null || event.getMetricType() == null) {
                continue;
            }
            MetricCounter counter = counters.computeIfAbsent(event.getPostId(), ignored -> new MetricCounter());
            counter.add(event.getMetricType(), event.getCountDelta());
        }

        List<HotScore> postScores = new ArrayList<>();
        for (Map.Entry<Long, MetricCounter> entry : counters.entrySet()) {
            RankingPostHotState state = stateMap.get(entry.getKey());
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
                .limit(snapshotProperties.getTopSize())
                .toList();
    }

    private PostStats toPostStats(RankingPostHotState state) {
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
