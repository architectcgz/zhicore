package com.zhicore.ranking.infrastructure.config;

import com.zhicore.ranking.application.service.ScoreBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 排行榜缓冲区优雅停机管理
 *
 * <p>通过 SmartLifecycle（phase = Integer.MAX_VALUE - 1）注册关闭钩子，
 * 优先于其他 Bean 销毁前执行刷写。</p>
 *
 * <p>关闭流程：</p>
 * <ol>
 *   <li>停止接收新事件（MQ Consumer 先关闭）</li>
 *   <li>执行最后一次 swap-and-flush</li>
 *   <li>等待刷写完成或超时（10s）</li>
 * </ol>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreBufferLifecycle implements SmartLifecycle {

    private final ScoreBufferService scoreBufferService;

    /** 最大等待时间（毫秒），需小于 K8s terminationGracePeriodSeconds */
    private static final long SHUTDOWN_TIMEOUT_MS = 10_000;

    private volatile boolean running = false;

    @Override
    public void start() {
        this.running = true;
        log.info("ScoreBufferLifecycle 已启动");
    }

    @Override
    public void stop() {
        doShutdown();
    }

    @Override
    public void stop(Runnable callback) {
        doShutdown();
        callback.run();
    }

    private void doShutdown() {
        if (!running) {
            return;
        }
        running = false;

        log.info("开始优雅停机：刷写缓冲区残留数据，超时={}ms", SHUTDOWN_TIMEOUT_MS);
        long startTime = System.currentTimeMillis();

        try {
            // 步骤 1：停止接收新事件
            scoreBufferService.stopAccepting();

            // 步骤 2：执行最后一次 swap-and-flush
            int flushed = scoreBufferService.flush();
            long elapsed = System.currentTimeMillis() - startTime;

            if (elapsed > SHUTDOWN_TIMEOUT_MS) {
                log.warn("优雅停机刷写超时: 已耗时={}ms, 超时阈值={}ms, 已刷写={}条",
                        elapsed, SHUTDOWN_TIMEOUT_MS, flushed);
            } else {
                log.info("优雅停机刷写完成: 刷写={}条, 耗时={}ms", flushed, elapsed);
            }

            // 检查是否还有残留数据
            int remaining = scoreBufferService.getBufferSize();
            if (remaining > 0) {
                log.warn("优雅停机后仍有 {} 条缓冲数据未刷写（可能是刷写失败回填的）", remaining);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("优雅停机刷写异常: 耗时={}ms", elapsed, e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 高优先级关闭（越大越先关闭）
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
