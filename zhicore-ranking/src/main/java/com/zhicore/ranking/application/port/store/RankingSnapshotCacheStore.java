package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.domain.model.HotScore;

import java.time.LocalDate;
import java.util.List;

/**
 * 排行快照缓存存储端口。
 *
 * 封装快照刷新的 Redis key 与 TTL 细节。
 */
public interface RankingSnapshotCacheStore {

    void replaceTotalRanking(List<HotScore> postScores, List<HotScore> creatorScores, List<HotScore> topicScores);

    void replaceDailyRanking(LocalDate date, List<HotScore> postScores, List<HotScore> creatorScores, List<HotScore> topicScores);

    void replaceWeeklyRanking(int weekBasedYear, int weekNumber, List<HotScore> postScores, List<HotScore> creatorScores, List<HotScore> topicScores);

    void replaceMonthlyRanking(int year, int month, List<HotScore> postScores, List<HotScore> creatorScores, List<HotScore> topicScores);
}
