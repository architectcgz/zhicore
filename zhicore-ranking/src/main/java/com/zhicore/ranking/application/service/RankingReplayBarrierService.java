package com.zhicore.ranking.application.service;

import com.zhicore.ranking.infrastructure.pg.PgRankingLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * replay 开始前等待所有已开始的 ingestion 事务排空。
 */
@Service
@RequiredArgsConstructor
public class RankingReplayBarrierService {

    private final PgRankingLedgerRepository repository;

    /**
     * 在独立事务中获取 replay exclusive barrier。
     *
     * <p>该事务提交前会等待所有已获取 shared barrier 的 ingestion 事务结束，
     * 提交后即可保证 replay 期间不会再有旧事务继续写 bucket/post_state。</p>
     */
    @Transactional
    public void awaitInFlightIngestionDrain() {
        repository.awaitReplayBarrierDrain();
    }
}
