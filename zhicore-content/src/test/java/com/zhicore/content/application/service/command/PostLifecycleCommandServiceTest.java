package com.zhicore.content.application.service.command;

import com.zhicore.content.application.command.handlers.DeletePostHandler;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.service.OwnedPostLoadService;
import com.zhicore.content.application.service.PostContentImageCleanupService;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostLifecycleCommandServiceTest {

    @Mock
    private OwnedPostLoadService ownedPostLoadService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostCoverImageCommandService postCoverImageCommandService;

    @Mock
    private DeletePostHandler deletePostHandler;

    @Mock
    private PostContentImageCleanupService postContentImageCleanupService;

    @InjectMocks
    private PostLifecycleCommandService postLifecycleCommandService;

    @Test
    void shouldUnpublishOwnedPost() {
        Post post = Post.reconstitute(new Post.Snapshot(
                PostId.of(2001L),
                UserId.of(1001L),
                null,
                "title",
                "excerpt",
                null,
                PostStatus.PUBLISHED,
                null,
                Set.of(),
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                false,
                PostStats.empty(PostId.of(2001L)),
                WriteState.PUBLISHED,
                null,
                1L
        ));
        when(ownedPostLoadService.load(2001L, 1001L)).thenReturn(post);

        postLifecycleCommandService.unpublishPost(1001L, 2001L);

        verify(postRepository).update(post);
    }

    @Test
    void shouldDeletePostAndIgnoreCleanupFailure() {
        Post post = Post.createDraft(PostId.of(2001L), UserId.of(1001L), "title");
        post.updateMeta("title", "excerpt", "file-1");
        when(ownedPostLoadService.load(2001L, 1001L)).thenReturn(post);
        doThrow(new RuntimeException("cleanup failed"))
                .when(postContentImageCleanupService)
                .cleanupContentImagesAsync(2001L);

        postLifecycleCommandService.deletePost(1001L, 2001L);

        verify(postCoverImageCommandService).deleteCoverImage(2001L, "file-1");
        verify(deletePostHandler).handle(any());
        verify(postContentImageCleanupService).cleanupContentImagesAsync(2001L);
    }
}
