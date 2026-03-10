package com.zhicore.ranking.infrastructure.mongodb;

import com.zhicore.ranking.application.model.RankingArchiveRecord;
import com.zhicore.ranking.application.port.store.RankingArchiveStore;
import com.zhicore.ranking.domain.model.HotScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

/**
 * 基于 MongoDB 的排行榜归档读取实现。
 */
@Component
@RequiredArgsConstructor
public class MongoRankingArchiveStore implements RankingArchiveStore {

    private static final String MONTHLY_RANKING_TYPE = "monthly";

    private final RankingArchiveRepository rankingArchiveRepository;

    @Override
    public List<HotScore> getMonthlyArchive(String entityType, int year, int month, int limit) {
        return rankingArchiveRepository
                .findByEntityTypeAndRankingTypeAndPeriod_YearAndPeriod_MonthOrderByRankAsc(
                        entityType,
                        MONTHLY_RANKING_TYPE,
                        year,
                        month
                )
                .stream()
                .limit(limit)
                .map(archive -> HotScore.builder()
                        .entityId(archive.getEntityId())
                        .score(archive.getScore())
                        .rank(archive.getRank())
                        .updatedAt(archive.getArchivedAt())
                        .build())
                .toList();
    }

    @Override
    public boolean saveIfAbsent(RankingArchiveRecord record) {
        boolean exists = rankingArchiveRepository
                .existsByEntityIdAndEntityTypeAndRankingTypeAndPeriod_YearAndPeriod_MonthAndPeriod_WeekAndPeriod_Date(
                        record.getEntityId(),
                        record.getEntityType(),
                        record.getRankingType(),
                        record.getYear(),
                        record.getMonth(),
                        record.getWeek(),
                        record.getDate()
                );
        if (exists) {
            return false;
        }

        rankingArchiveRepository.save(RankingArchive.builder()
                .entityId(record.getEntityId())
                .entityType(record.getEntityType())
                .score(record.getScore())
                .rank(record.getRank())
                .rankingType(record.getRankingType())
                .period(RankingArchive.PeriodInfo.builder()
                        .year(record.getYear())
                        .month(record.getMonth())
                        .week(record.getWeek())
                        .date(record.getDate())
                        .build())
                .metadata(new HashMap<>())
                .archivedAt(record.getArchivedAt())
                .version(1)
                .build());
        return true;
    }
}
