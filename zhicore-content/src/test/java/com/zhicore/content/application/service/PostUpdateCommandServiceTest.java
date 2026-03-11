package com.zhicore.content.application.service;

import com.zhicore.content.application.command.UpdatePostAppCommand;
import com.zhicore.content.application.command.handlers.UpdatePostContentHandler;
import com.zhicore.content.application.command.handlers.UpdatePostMetaHandler;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostUpdateCommandServiceTest {

    @Mock
    private OwnedPostLoadService ownedPostLoadService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostTagCommandService postTagCommandService;

    @Mock
    private PostCoverImageCommandService postCoverImageCommandService;

    @Mock
    private UpdatePostMetaHandler updatePostMetaHandler;

    @Mock
    private UpdatePostContentHandler updatePostContentHandler;

    @Mock
    private IntegrationEventPublisher integrationEventPublisher;

    @InjectMocks
    private PostUpdateCommandService postUpdateCommandService;

    @Test
    void shouldUpdatePostWithoutPublishedEventForDraft() {
        Post post = Post.createDraft(PostId.of(2001L), UserId.of(1001L), "title");
        UpdatePostAppCommand command = new UpdatePostAppCommand("新标题", "新内容", 8L, "file-2", List.of("Java"));

        when(ownedPostLoadService.load(2001L, 1001L)).thenReturn(post);
        when(postTagCommandService.replaceTags(2001L, List.of("Java")))
                .thenReturn(new PostTagCommandService.ReplaceResult(List.of(), List.of(11L)));

        postUpdateCommandService.updatePost(1001L, 2001L, command);

        verify(postCoverImageCommandService).handleCoverImageChange(2001L, post.getCoverImageId(), "file-2");
        verify(postRepository).update(post);
        verify(updatePostMetaHandler).handle(any());
        verify(updatePostContentHandler).handle(any());
    }

    @Test
    void shouldSkipTagEventWhenTagsNotProvided() {
        Post post = Post.createDraft(PostId.of(2001L), UserId.of(1001L), "title");
        UpdatePostAppCommand command = new UpdatePostAppCommand("新标题", "新内容", null, null, null);

        when(ownedPostLoadService.load(2001L, 1001L)).thenReturn(post);

        postUpdateCommandService.updatePost(1001L, 2001L, command);

        verify(postTagCommandService, never()).replaceTags(any(), any());
    }
}
