package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.ranking.application.service.ScoreBufferService;
import com.zhicore.ranking.infrastructure.config.RankingBufferProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓冲区定时刷写调度器
 *
 * <p>定时唤醒 ScoreBufferService 执行 flush，与阈值触发互为补充。</p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BufferFlushScheduler {

    private final ScoreBufferService scoreBufferService;

    /**
     * 定时刷写缓冲区（默认 5 秒）
     */
    @Scheduled(fixedDelayString = "${ranking.buffer.flush-interval:5000}")
    public void scheduledFlush() {
        if (scoreBufferService.isStopped()) {
            return;
        }
        if (scoreBufferService.getBufferSize() == 0) {
            return;
        }
        try {
            scoreBufferService.flush();
        } catch (Exception e) {
            log.error("定时刷写缓冲区异常", e);
        }
    }
}
