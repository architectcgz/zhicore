package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.SnapshotPeriodScore;
import com.zhicore.ranking.application.model.SnapshotPostHotState;
import com.zhicore.ranking.application.port.policy.RankingSnapshotPolicy;
import com.zhicore.ranking.application.port.store.RankingSnapshotCacheStore;
import com.zhicore.ranking.application.port.store.RankingSnapshotSourceStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从权威状态和周期分数物化结果刷新 Redis 排行快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingSnapshotService {

    private static final int DAILY_RECOVERY_WINDOW_DAYS = 2;
    private static final int WEEKLY_RECOVERY_WINDOW_DAYS = 20;
    private static final int MONTHLY_RECOVERY_WINDOW_DAYS = 365;

    private final RankingSnapshotSourceStore snapshotSourceStore;
    private final RankingSnapshotCacheStore snapshotCacheStore;
    private final RankingSnapshotPolicy snapshotPolicy;
    private final HotScoreCalculator hotScoreCalculator;

    public void refreshCurrentSnapshots() {
        SnapshotContext context = loadSnapshotContext();
        refreshTotalRanking(context);

        LocalDate today = LocalDate.now();
        refreshDailyRanking(today, context);
        refreshWeeklyRanking(RankingRedisKeys.getWeekBasedYear(today), RankingRedisKeys.getWeekNumber(today), context);
        refreshMonthlyRanking(today.getYear(), today.getMonthValue(), context);
    }

    public void refreshActiveSnapshots() {
        SnapshotContext context = loadSnapshotContext();
        refreshTotalRanking(context);

        LocalDate today = LocalDate.now();
        for (LocalDate date : activeDailyDates(today)) {
            refreshDailyRanking(date, context);
        }
        for (WeekPeriod period : activeWeeklyPeriods(today)) {
            refreshWeeklyRanking(period.weekBasedYear(), period.weekNumber(), context);
        }
        for (YearMonth period : activeMonthlyPeriods(today)) {
            refreshMonthlyRanking(period.getYear(), period.getMonthValue(), context);
        }
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

    private PeriodSnapshot buildPeriodSnapshot(List<SnapshotPeriodScore> periodScores,
                                               Map<Long, SnapshotPostHotState> stateMap) {
        if (periodScores == null || periodScores.isEmpty()) {
            return new PeriodSnapshot(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        List<HotScore> postScores = new ArrayList<>();
        for (SnapshotPeriodScore periodScore : periodScores) {
            SnapshotPostHotState state = stateMap.get(periodScore.getPostId());
            if (state == null) {
                continue;
            }
            double score = periodScore.getDeltaScore();
            if (score > 0) {
                postScores.add(HotScore.of(String.valueOf(periodScore.getPostId()), score));
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

    private SnapshotContext loadSnapshotContext() {
        List<SnapshotPostHotState> states = snapshotSourceStore.listActivePostStates();
        Map<Long, SnapshotPostHotState> stateMap = new HashMap<>();
        states.forEach(state -> stateMap.put(state.getPostId(), state));
        return new SnapshotContext(states, stateMap);
    }

    private void refreshTotalRanking(SnapshotContext context) {
        List<HotScore> totalPostRanking = buildPostScores(context.states());
        List<HotScore> totalCreatorRanking = buildCreatorScores(totalPostRanking, context.stateMap());
        List<HotScore> totalTopicRanking = buildTopicScores(totalPostRanking, context.stateMap());
        snapshotCacheStore.replaceTotalRanking(totalPostRanking, totalCreatorRanking, totalTopicRanking);
    }

    private void refreshDailyRanking(LocalDate date, SnapshotContext context) {
        PeriodSnapshot snapshot = buildPeriodSnapshot(
                snapshotSourceStore.listPeriodScores("DAY", date.toString()),
                context.stateMap()
        );
        snapshotCacheStore.replaceDailyRanking(date, snapshot.postScores(), snapshot.creatorScores(), snapshot.topicScores());
    }

    private void refreshWeeklyRanking(int weekBasedYear, int weekNumber, SnapshotContext context) {
        String weekKey = "%d-W%02d".formatted(weekBasedYear, weekNumber);
        PeriodSnapshot snapshot = buildPeriodSnapshot(
                snapshotSourceStore.listPeriodScores("WEEK", weekKey),
                context.stateMap()
        );
        snapshotCacheStore.replaceWeeklyRanking(
                weekBasedYear,
                weekNumber,
                snapshot.postScores(),
                snapshot.creatorScores(),
                snapshot.topicScores()
        );
    }

    private void refreshMonthlyRanking(int year, int month, SnapshotContext context) {
        String monthKey = "%d-%02d".formatted(year, month);
        PeriodSnapshot snapshot = buildPeriodSnapshot(
                snapshotSourceStore.listPeriodScores("MONTH", monthKey),
                context.stateMap()
        );
        snapshotCacheStore.replaceMonthlyRanking(
                year,
                month,
                snapshot.postScores(),
                snapshot.creatorScores(),
                snapshot.topicScores()
        );
    }

    private List<LocalDate> activeDailyDates(LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();
        for (int daysAgo = DAILY_RECOVERY_WINDOW_DAYS; daysAgo >= 0; daysAgo--) {
            dates.add(today.minusDays(daysAgo));
        }
        return dates;
    }

    private List<WeekPeriod> activeWeeklyPeriods(LocalDate today) {
        Set<WeekPeriod> periods = new LinkedHashSet<>();
        LocalDate start = today.minusDays(WEEKLY_RECOVERY_WINDOW_DAYS);
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            periods.add(new WeekPeriod(RankingRedisKeys.getWeekBasedYear(date), RankingRedisKeys.getWeekNumber(date)));
        }
        return List.copyOf(periods);
    }

    private List<YearMonth> activeMonthlyPeriods(LocalDate today) {
        LocalDate start = today.minusDays(MONTHLY_RECOVERY_WINDOW_DAYS).withDayOfMonth(1);
        YearMonth end = YearMonth.from(today);
        List<YearMonth> periods = new ArrayList<>();
        for (YearMonth current = YearMonth.from(start); !current.isAfter(end); current = current.plusMonths(1)) {
            periods.add(current);
        }
        return periods;
    }

    private record PeriodSnapshot(List<HotScore> postScores,
                                  List<HotScore> creatorScores,
                                  List<HotScore> topicScores) {
    }

    private record SnapshotContext(List<SnapshotPostHotState> states,
                                   Map<Long, SnapshotPostHotState> stateMap) {
    }

    private record WeekPeriod(int weekBasedYear, int weekNumber) {
    }
}
