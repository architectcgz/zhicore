package com.zhicore.content.application.command.handlers;

import com.zhicore.content.application.command.commands.DeletePostCommand;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostCacheInvalidationStore;
import com.zhicore.content.application.service.PostReaderPresenceAppService;
import com.zhicore.content.domain.event.DomainEventFactory;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletePostHandlerPresenceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private PostCacheInvalidationStore postCacheInvalidationStore;
    @Mock
    private DomainEventFactory eventFactory;
    @Mock
    private PostReaderPresenceAppService postReaderPresenceAppService;

    @InjectMocks
    private DeletePostHandler handler;

    @Test
    void shouldEvictPresenceAfterDelete() {
        Long postId = 1001L;
        Long userId = 2001L;
        Post post = Post.createDraft(PostId.of(postId), UserId.of(userId), "title");

        when(postRepository.load(PostId.of(postId))).thenReturn(post);
        when(eventFactory.generateEventId()).thenReturn("evt-delete");
        when(eventFactory.now()).thenReturn(Instant.parse("2026-03-28T08:00:00Z"));

        handler.handle(new DeletePostCommand(PostId.of(postId), UserId.of(userId)));

        verify(postReaderPresenceAppService).evictPost(postId);
    }
}
