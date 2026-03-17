package com.zhicore.common.mq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalEventPublisher 测试")
class TransactionalEventPublisherTest {

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("存在事务同步时应该在 afterCommit 中同步发送事件")
    void shouldPublishAfterCommitWithSyncPublisher() {
        TransactionalEventPublisher publisher = new TransactionalEventPublisher(domainEventPublisher);
        SampleEvent event = new SampleEvent("evt-1");
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit("topic-a", "tag-a", event);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(domainEventPublisher).publishSync("topic-a", "tag-a", event);
    }

    @Test
    @DisplayName("不存在事务时应该直接同步发送事件")
    void shouldPublishImmediatelyWithoutTransaction() {
        TransactionalEventPublisher publisher = new TransactionalEventPublisher(domainEventPublisher);
        SampleEvent event = new SampleEvent("evt-2");

        publisher.publishAfterCommit("topic-a", "tag-a", event);

        verify(domainEventPublisher).publishSync("topic-a", "tag-a", event);
    }

    @Test
    @DisplayName("afterCommit 构建器应该注册同步发送动作")
    void shouldRegisterSyncPublishActions() {
        TransactionalEventPublisher publisher = new TransactionalEventPublisher(domainEventPublisher);
        SampleEvent directEvent = new SampleEvent("evt-3");
        SampleEvent supplierEvent = new SampleEvent("evt-4");
        TransactionSynchronizationManager.initSynchronization();

        publisher.afterCommit()
                .publishEvent("topic-a", "tag-a", directEvent)
                .publishEvent("topic-b", "tag-b", () -> supplierEvent)
                .register();

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(domainEventPublisher).publishSync("topic-a", "tag-a", directEvent);
        verify(domainEventPublisher).publishSync("topic-b", "tag-b", supplierEvent);
    }

    private record SampleEvent(String id) {
    }
}
