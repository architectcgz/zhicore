package com.zhicore.migration.service.cdc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CdcEventDispatcherTest {

    @Mock
    private PostStatsCdcConsumer postStatsCdcConsumer;
    @Mock
    private PostLikesCdcConsumer postLikesCdcConsumer;
    @Mock
    private CommentStatsCdcConsumer commentStatsCdcConsumer;
    @Mock
    private CommentLikesCdcConsumer commentLikesCdcConsumer;
    @Mock
    private UserFollowStatsCdcConsumer userFollowStatsCdcConsumer;

    @Test
    void shouldDispatchToPostStatsConsumer() {
        CdcEventDispatcher dispatcher = new CdcEventDispatcher(
                postStatsCdcConsumer,
                postLikesCdcConsumer,
                commentStatsCdcConsumer,
                commentLikesCdcConsumer,
                userFollowStatsCdcConsumer
        );
        CdcEvent event = CdcEvent.builder()
                .table("post_stats")
                .operation(CdcEvent.Operation.UPDATE)
                .build();

        dispatcher.dispatch(event);

        verify(postStatsCdcConsumer).consume(event);
        verifyNoInteractions(postLikesCdcConsumer, commentStatsCdcConsumer, commentLikesCdcConsumer, userFollowStatsCdcConsumer);
    }
}
