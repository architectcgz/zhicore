package com.zhicore.ranking.application.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 排行榜归档记录模型。
 */
@Value
@Builder
public class RankingArchiveRecord {

    String entityId;
    String entityType;
    double score;
    int rank;
    String rankingType;
    Integer year;
    Integer month;
    Integer week;
    LocalDate date;
    OffsetDateTime archivedAt;
}
