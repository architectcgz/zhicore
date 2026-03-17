package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.domain.model.HotScore;

import java.time.LocalDate;
import java.util.List;

/**
 * 排行榜归档源数据端口。
 *
 * 封装归档任务从 Redis 读取榜单数据的细节。
 */
public interface RankingArchiveSourceStore {

    List<HotScore> getDailyPostRanking(LocalDate date, int limit);

    List<HotScore> getDailyCreatorRanking(LocalDate date, int limit);

    List<HotScore> getDailyTopicRanking(LocalDate date, int limit);

    List<HotScore> getWeeklyPostRanking(int weekBasedYear, int weekNumber, int limit);

    List<HotScore> getWeeklyCreatorRanking(int weekBasedYear, int weekNumber, int limit);

    List<HotScore> getWeeklyTopicRanking(int weekBasedYear, int weekNumber, int limit);

    List<HotScore> getMonthlyPostRanking(int year, int month, int limit);

    List<HotScore> getMonthlyCreatorRanking(int year, int month, int limit);

    List<HotScore> getMonthlyTopicRanking(int year, int month, int limit);
}
