package com.zhicore.ranking.infrastructure.pg;

import com.zhicore.ranking.application.model.SnapshotPeriodScore;
import com.zhicore.ranking.application.model.SnapshotPostHotState;
import com.zhicore.ranking.application.port.store.RankingSnapshotSourceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 PostgreSQL 物化状态的排行快照数据源。
 */
@Primary
@Component
@RequiredArgsConstructor
public class PgRankingSnapshotSourceStore implements RankingSnapshotSourceStore {

    private final PgRankingLedgerRepository repository;

    @Override
    public List<SnapshotPostHotState> listActivePostStates() {
        return repository.listSnapshotPostStates();
    }

    @Override
    public List<SnapshotPeriodScore> listPeriodScores(String periodType, String periodKey) {
        return repository.listPeriodScores(periodType, periodKey);
    }
}
