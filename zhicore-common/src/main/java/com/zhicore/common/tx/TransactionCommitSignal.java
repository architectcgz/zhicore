package com.zhicore.common.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后执行辅助器。
 *
 * <p>统一收口“业务事务提交成功后再触发异步派发”的时机控制，
 * 避免在事务未提交时启动 outbox 派发导致读取不到新写入事件。
 */
@Slf4j
@Component
public class TransactionCommitSignal {

    public void afterCommit(Runnable action) {
        if (action == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            runSafely(action);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(action);
            }
        });
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("事务提交后执行回调失败: {}", ex.getMessage(), ex);
        }
    }
}
