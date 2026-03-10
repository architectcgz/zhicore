package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.application.model.SnapshotInboxEvent;
import com.zhicore.ranking.application.model.SnapshotPostHotState;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 排行快照数据源端口。
 *
 * 封装权威状态和 DONE inbox 事件读取，避免 application 暴露 Mongo 文档。
 */
public interface RankingSnapshotSourceStore {

    List<SnapshotPostHotState> listActivePostStates();

    List<SnapshotInboxEvent> listDoneEventsBetween(LocalDateTime start, LocalDateTime end);
}
