package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.TopicRankingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TopicRankingService Tests")
class TopicRankingServiceTest {

    @Mock
    private TopicRankingStore topicRankingStore;

    @Test
    @DisplayName("增量更新话题热度时应委托给 store")
    void incrementTopicScore_shouldDelegateToStore() {
        TopicRankingService service = new TopicRankingService(topicRankingStore);

        service.incrementTopicScore(3001L, 2.5);

        verify(topicRankingStore).incrementTopicScore(3001L, 2.5);
    }

    @Test
    @DisplayName("获取话题排名时应直接返回 store 结果")
    void getTopicRank_shouldReturnStoreValue() {
        TopicRankingService service = new TopicRankingService(topicRankingStore);
        when(topicRankingStore.getTopicRank(3001L)).thenReturn(4L);

        service.getTopicRank(3001L);

        verify(topicRankingStore).getTopicRank(3001L);
    }
}
