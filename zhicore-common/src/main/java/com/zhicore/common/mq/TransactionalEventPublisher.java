package com.zhicore.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 事务性事件发布器
 * 
 * 确保事件在数据库事务提交后才发布，避免数据不一致问题
 * 
 * 使用场景：
 * 1. 点赞操作：先保存点赞记录到数据库，事务提交后再更新 Redis 计数和发布事件
 * 2. 关注操作：先保存关注关系到数据库，事务提交后再更新 Redis 和发布事件
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class TransactionalEventPublisher {

    private final DomainEventPublisher eventPublisher;

    /**
     * 在事务提交后发布事件
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     */
    public void publishAfterCommit(String topic, String tag, Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            eventPublisher.publish(topic, tag, event);
                            log.debug("Event published after commit: topic={}, tag={}", topic, tag);
                        } catch (Exception e) {
                            log.error("Failed to publish event after commit: topic={}, tag={}", 
                                    topic, tag, e);
                        }
                    }
                }
            );
        } else {
            // 如果没有活跃的事务，直接发布
            log.warn("No active transaction, publishing event immediately: topic={}, tag={}", topic, tag);
            eventPublisher.publish(topic, tag, event);
        }
    }

    /**
     * 在事务提交后执行操作
     *
     * @param action 要执行的操作
     */
    public void executeAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            action.run();
                        } catch (Exception e) {
                            log.error("Failed to execute action after commit", e);
                        }
                    }
                }
            );
        } else {
            log.warn("No active transaction, executing action immediately");
            action.run();
        }
    }

    /**
     * 在事务提交后执行多个操作
     *
     * @param actions 要执行的操作列表
     */
    public void executeAfterCommit(List<Runnable> actions) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (Runnable action : actions) {
                            try {
                                action.run();
                            } catch (Exception e) {
                                log.error("Failed to execute action after commit", e);
                            }
                        }
                    }
                }
            );
        } else {
            log.warn("No active transaction, executing actions immediately");
            for (Runnable action : actions) {
                action.run();
            }
        }
    }

    /**
     * 在事务回滚后执行操作
     *
     * @param action 要执行的操作
     */
    public void executeAfterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                action.run();
                            } catch (Exception e) {
                                log.error("Failed to execute action after rollback", e);
                            }
                        }
                    }
                }
            );
        }
    }

    /**
     * 创建一个事务后操作构建器
     *
     * @return 构建器
     */
    public AfterCommitBuilder afterCommit() {
        return new AfterCommitBuilder(this);
    }

    /**
     * 事务后操作构建器
     */
    public static class AfterCommitBuilder {
        private final TransactionalEventPublisher publisher;
        private final List<Runnable> actions = new ArrayList<>();

        AfterCommitBuilder(TransactionalEventPublisher publisher) {
            this.publisher = publisher;
        }

        /**
         * 添加要执行的操作
         */
        public AfterCommitBuilder execute(Runnable action) {
            actions.add(action);
            return this;
        }

        /**
         * 添加要发布的事件
         */
        public AfterCommitBuilder publishEvent(String topic, String tag, Object event) {
            actions.add(() -> publisher.eventPublisher.publish(topic, tag, event));
            return this;
        }

        /**
         * 添加要发布的事件（延迟获取事件对象）
         */
        public AfterCommitBuilder publishEvent(String topic, String tag, Supplier<Object> eventSupplier) {
            actions.add(() -> publisher.eventPublisher.publish(topic, tag, eventSupplier.get()));
            return this;
        }

        /**
         * 注册所有操作
         */
        public void register() {
            publisher.executeAfterCommit(actions);
        }
    }
}
