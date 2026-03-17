package com.zhicore.ranking.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Ranking ledger 全量补算结果。
 */
@Value
@Builder
public class RankingReplayResultDTO {

    int replayedEvents;
    LocalDateTime rebuiltAt;
}
