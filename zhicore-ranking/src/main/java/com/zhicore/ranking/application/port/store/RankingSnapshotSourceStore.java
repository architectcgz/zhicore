package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.application.model.SnapshotPeriodScore;
import com.zhicore.ranking.application.model.SnapshotPostHotState;

import java.util.List;

/**
 * 排行快照数据源端口。
 *
 * 封装权威状态和周期物化分数读取，避免 application 直接依赖持久化实现。
 */
public interface RankingSnapshotSourceStore {

    List<SnapshotPostHotState> listActivePostStates();

    List<SnapshotPeriodScore> listPeriodScores(String periodType, String periodKey);
}
