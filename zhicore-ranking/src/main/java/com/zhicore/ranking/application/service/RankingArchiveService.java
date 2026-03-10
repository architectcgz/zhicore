package com.zhicore.ranking.application.service;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.model.RankingArchiveRecord;
import com.zhicore.ranking.application.port.policy.RankingArchivePolicy;
import com.zhicore.ranking.application.port.store.RankingArchiveSourceStore;
import com.zhicore.ranking.application.port.store.RankingArchiveStore;
import com.zhicore.ranking.domain.model.HotScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * 排行榜归档服务。
 */
@Slf4j
@Service
public class RankingArchiveService {

    private static final String POST_ENTITY_TYPE = "post";
    private static final String CREATOR_ENTITY_TYPE = "creator";
    private static final String TOPIC_ENTITY_TYPE = "topic";
    private static final String DAILY_RANKING_TYPE = "daily";
    private static final String WEEKLY_RANKING_TYPE = "weekly";
    private static final String MONTHLY_RANKING_TYPE = "monthly";

    private final RankingArchiveSourceStore rankingArchiveSourceStore;
    private final RankingArchiveStore rankingArchiveStore;
    private final RankingArchivePolicy rankingArchivePolicy;
    private final DistributedLockExecutor lockExecutor;

    public RankingArchiveService(RankingArchiveSourceStore rankingArchiveSourceStore,
                                 RankingArchiveStore rankingArchiveStore,
                                 RankingArchivePolicy rankingArchivePolicy,
                                 DistributedLockExecutor lockExecutor) {
        this.rankingArchiveSourceStore = rankingArchiveSourceStore;
        this.rankingArchiveStore = rankingArchiveStore;
        this.rankingArchivePolicy = rankingArchivePolicy;
        this.lockExecutor = lockExecutor;
    }

    @Scheduled(cron = "${ranking.scheduler.daily-archive-cron:0 0 2 * * ?}")
    public void archiveDailyRanking() {
        lockExecutor.executeWithLock(rankingArchivePolicy.dailyArchiveLockKey(), () -> {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            log.info("开始归档日榜: date={}", yesterday);
            try {
                archiveDailyPosts(yesterday);
                archiveDailyCreators(yesterday);
                archiveDailyTopics(yesterday);
                log.info("日榜归档完成: date={}", yesterday);
            } catch (Exception e) {
                log.error("日榜归档失败: date={}", yesterday, e);
            }
        });
    }

    @Scheduled(cron = "${ranking.scheduler.weekly-archive-cron:0 0 3 ? * MON}")
    public void archiveWeeklyRanking() {
        lockExecutor.executeWithLock(rankingArchivePolicy.weeklyArchiveLockKey(), () -> {
            LocalDate lastWeek = LocalDate.now().minusWeeks(1);
            int year = lastWeek.getYear();
            int weekNumber = getWeekNumber(lastWeek);
            log.info("开始归档周榜: year={}, week={}", year, weekNumber);
            try {
                archiveWeeklyPosts(year, weekNumber);
                archiveWeeklyCreators(year, weekNumber);
                archiveWeeklyTopics(year, weekNumber);
                log.info("周榜归档完成: year={}, week={}", year, weekNumber);
            } catch (Exception e) {
                log.error("周榜归档失败: year={}, week={}", year, weekNumber, e);
            }
        });
    }

    @Scheduled(cron = "${ranking.scheduler.monthly-archive-cron:0 0 4 1 * ?}")
    public void archiveMonthlyRanking() {
        lockExecutor.executeWithLock(rankingArchivePolicy.monthlyArchiveLockKey(), () -> {
            LocalDate lastMonth = LocalDate.now().minusMonths(1);
            int year = lastMonth.getYear();
            int month = lastMonth.getMonthValue();
            log.info("开始归档月榜: year={}, month={}", year, month);
            try {
                archiveMonthlyPosts(year, month);
                archiveMonthlyCreators(year, month);
                archiveMonthlyTopics(year, month);
                log.info("月榜归档完成: year={}, month={}", year, month);
            } catch (Exception e) {
                log.error("月榜归档失败: year={}, month={}", year, month, e);
            }
        });
    }

    public List<HotScore> getMonthlyArchive(String entityType, int year, int month, int limit) {
        return rankingArchiveStore.getMonthlyArchive(entityType, year, month, limit);
    }

    private void archiveDailyPosts(LocalDate date) {
        archiveSingleDimension(
                "文章日榜",
                rankingArchiveSourceStore.getDailyPostRanking(date, rankingArchivePolicy.archiveLimit()),
                POST_ENTITY_TYPE,
                DAILY_RANKING_TYPE,
                date.getYear(),
                null,
                null,
                date
        );
    }

    private void archiveDailyCreators(LocalDate date) {
        archiveSingleDimension(
                "创作者日榜",
                rankingArchiveSourceStore.getDailyCreatorRanking(rankingArchivePolicy.archiveLimit()),
                CREATOR_ENTITY_TYPE,
                DAILY_RANKING_TYPE,
                date.getYear(),
                null,
                null,
                date
        );
    }

    private void archiveDailyTopics(LocalDate date) {
        archiveSingleDimension(
                "话题日榜",
                rankingArchiveSourceStore.getDailyTopicRanking(rankingArchivePolicy.archiveLimit()),
                TOPIC_ENTITY_TYPE,
                DAILY_RANKING_TYPE,
                date.getYear(),
                null,
                null,
                date
        );
    }

    private void archiveWeeklyPosts(int year, int weekNumber) {
        archiveSingleDimension(
                "文章周榜",
                rankingArchiveSourceStore.getWeeklyPostRanking(weekNumber, rankingArchivePolicy.archiveLimit()),
                POST_ENTITY_TYPE,
                WEEKLY_RANKING_TYPE,
                year,
                null,
                weekNumber,
                null
        );
    }

    private void archiveWeeklyCreators(int year, int weekNumber) {
        archiveSingleDimension(
                "创作者周榜",
                rankingArchiveSourceStore.getWeeklyCreatorRanking(rankingArchivePolicy.archiveLimit()),
                CREATOR_ENTITY_TYPE,
                WEEKLY_RANKING_TYPE,
                year,
                null,
                weekNumber,
                null
        );
    }

    private void archiveWeeklyTopics(int year, int weekNumber) {
        archiveSingleDimension(
                "话题周榜",
                rankingArchiveSourceStore.getWeeklyTopicRanking(rankingArchivePolicy.archiveLimit()),
                TOPIC_ENTITY_TYPE,
                WEEKLY_RANKING_TYPE,
                year,
                null,
                weekNumber,
                null
        );
    }

    private void archiveMonthlyPosts(int year, int month) {
        archiveSingleDimension(
                "文章月榜",
                rankingArchiveSourceStore.getMonthlyPostRanking(year, month, rankingArchivePolicy.archiveLimit()),
                POST_ENTITY_TYPE,
                MONTHLY_RANKING_TYPE,
                year,
                month,
                null,
                null
        );
    }

    private void archiveMonthlyCreators(int year, int month) {
        archiveSingleDimension(
                "创作者月榜",
                rankingArchiveSourceStore.getMonthlyCreatorRanking(rankingArchivePolicy.archiveLimit()),
                CREATOR_ENTITY_TYPE,
                MONTHLY_RANKING_TYPE,
                year,
                month,
                null,
                null
        );
    }

    private void archiveMonthlyTopics(int year, int month) {
        archiveSingleDimension(
                "话题月榜",
                rankingArchiveSourceStore.getMonthlyTopicRanking(rankingArchivePolicy.archiveLimit()),
                TOPIC_ENTITY_TYPE,
                MONTHLY_RANKING_TYPE,
                year,
                month,
                null,
                null
        );
    }

    private void archiveSingleDimension(String label,
                                        List<HotScore> scores,
                                        String entityType,
                                        String rankingType,
                                        Integer year,
                                        Integer month,
                                        Integer week,
                                        LocalDate date) {
        try {
            log.info("开始归档{}: year={}, month={}, week={}, date={}", label, year, month, week, date);
            int archived = archiveRankingData(scores, entityType, rankingType, year, month, week, date);
            log.info("{}归档完成: archived={}", label, archived);
        } catch (Exception e) {
            log.error("{}归档失败: year={}, month={}, week={}, date={}", label, year, month, week, date, e);
        }
    }

    private int archiveRankingData(List<HotScore> scores,
                                   String entityType,
                                   String rankingType,
                                   Integer year,
                                   Integer month,
                                   Integer week,
                                   LocalDate date) {
        int archived = 0;
        for (HotScore score : scores) {
            boolean saved = rankingArchiveStore.saveIfAbsent(RankingArchiveRecord.builder()
                    .entityId(score.getEntityId())
                    .entityType(entityType)
                    .score(score.getScore())
                    .rank(score.getRank())
                    .rankingType(rankingType)
                    .year(year)
                    .month(month)
                    .week(week)
                    .date(date)
                    .archivedAt(LocalDateTime.now())
                    .build());
            if (saved) {
                archived++;
            }
        }
        return archived;
    }

    private int getWeekNumber(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return date.get(weekFields.weekOfWeekBasedYear());
    }
}
