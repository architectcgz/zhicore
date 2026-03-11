package com.zhicore.migration.service.cdc;

import com.zhicore.migration.service.cdc.store.PostLikesCacheStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostLikesCdcConsumerTest {

    @Mock
    private PostLikesCacheStore postLikesCacheStore;

    @Test
    void shouldDelegateCreateToStore() {
        PostLikesCdcConsumer consumer = new PostLikesCdcConsumer(postLikesCacheStore);
        CdcEvent event = CdcEvent.builder()
                .operation(CdcEvent.Operation.CREATE)
                .after(Map.of("post_id", 101L, "user_id", 202L))
                .build();

        consumer.consume(event);

        verify(postLikesCacheStore).addLike("101", "202");
    }

    @Test
    void shouldDelegateDeleteToStore() {
        PostLikesCdcConsumer consumer = new PostLikesCdcConsumer(postLikesCacheStore);
        CdcEvent event = CdcEvent.builder()
                .operation(CdcEvent.Operation.DELETE)
                .before(Map.of("post_id", 101L, "user_id", 202L))
                .build();

        consumer.consume(event);

        verify(postLikesCacheStore).removeLike("101", "202");
    }
}
