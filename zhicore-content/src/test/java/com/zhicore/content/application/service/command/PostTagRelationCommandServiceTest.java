package com.zhicore.content.application.service.command;

import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.service.OwnedPostLoadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostTagRelationCommandServiceTest {

    @Mock
    private OwnedPostLoadService ownedPostLoadService;

    @Mock
    private PostTagCommandService postTagCommandService;

    @Mock
    private IntegrationEventPublisher integrationEventPublisher;

    @InjectMocks
    private PostTagRelationCommandService postTagRelationCommandService;

    @Test
    void shouldReplacePostTags() {
        Post post = Post.createDraft(PostId.of(2001L), UserId.of(1001L), "title");
        when(ownedPostLoadService.load(2001L, 1001L)).thenReturn(post);
        when(postTagCommandService.replaceTags(2001L, List.of("Java")))
                .thenReturn(new PostTagCommandService.ReplaceResult(List.of(1L), List.of(2L)));

        postTagRelationCommandService.replacePostTags(1001L, 2001L, List.of("Java"));

        verify(postTagCommandService).replaceTags(2001L, List.of("Java"));
        verify(integrationEventPublisher).publish(any());
    }

    @Test
    void shouldDetachTagByReusingReplaceLogic() {
        Post post = Post.createDraft(PostId.of(2001L), UserId.of(1001L), "title");
        when(ownedPostLoadService.load(2001L, 1001L)).thenReturn(post);
        when(postTagCommandService.listRemainingTagNames(2001L, "java")).thenReturn(List.of("Spring"));
        when(postTagCommandService.replaceTags(2001L, List.of("Spring")))
                .thenReturn(new PostTagCommandService.ReplaceResult(List.of(1L), List.of(3L)));

        postTagRelationCommandService.detachTag(1001L, 2001L, "java");

        verify(postTagCommandService).listRemainingTagNames(2001L, "java");
        verify(postTagCommandService).replaceTags(2001L, List.of("Spring"));
    }
}
