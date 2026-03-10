package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.application.model.RankingArchiveRecord;
import com.zhicore.ranking.domain.model.HotScore;

import java.util.List;

/**
 * 排行榜归档读取端口。
 *
 * 封装历史榜单查询，避免 application 暴露 Mongo 文档结构。
 */
public interface RankingArchiveStore {

    List<HotScore> getMonthlyArchive(String entityType, int year, int month, int limit);

    boolean saveIfAbsent(RankingArchiveRecord record);
}
