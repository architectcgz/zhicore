package com.zhicore.content.application.command.handlers;

import com.zhicore.common.context.UserContext;
import com.zhicore.content.application.command.commands.PurgePostCommand;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.application.port.store.PostCacheInvalidationStore;
import com.zhicore.content.application.service.PostReaderPresenceAppService;
import com.zhicore.content.domain.exception.PostOwnershipException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurgePostHandlerTask03Test {

    @Mock private PostRepository postRepository;
    @Mock private PostContentStore postContentStore;
    @Mock private EventPublisher eventPublisher;
    @Mock private PostCacheInvalidationStore postCacheInvalidationStore;
    @Mock private PostReaderPresenceAppService postReaderPresenceAppService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void nonAdmin_cannotPurge_notDeletedPost() {
        Long postId = 1L;
        Long ownerId = 10L;
        Long operatorId = 20L;

        Post post = buildPost(postId, ownerId, false);
        when(postRepository.load(PostId.of(postId))).thenReturn(post);

        setRole(operatorId, "user");

        PurgePostHandler handler = new PurgePostHandler(
                postRepository, postContentStore, eventPublisher, postCacheInvalidationStore, postReaderPresenceAppService);
        PurgePostCommand command = new PurgePostCommand(PostId.of(postId), UserId.of(operatorId));

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只有管理员可以物理删除未软删除的文章");
    }

    @Test
    void admin_canPurge_notDeletedPost() {
        Long postId = 2L;
        Long ownerId = 10L;
        Long operatorId = 99L;

        Post post = buildPost(postId, ownerId, false);
        when(postRepository.load(PostId.of(postId))).thenReturn(post);

        setRole(operatorId, "admin");

        PurgePostHandler handler = new PurgePostHandler(
                postRepository, postContentStore, eventPublisher, postCacheInvalidationStore, postReaderPresenceAppService);
        PurgePostCommand command = new PurgePostCommand(PostId.of(postId), UserId.of(operatorId));

        handler.handle(command);

        verify(postRepository).delete(PostId.of(postId));
        verify(postReaderPresenceAppService).evictPost(postId);
    }

    @Test
    void owner_canPurge_deletedPost() {
        Long postId = 3L;
        Long ownerId = 10L;

        Post post = buildPost(postId, ownerId, true);
        when(postRepository.load(PostId.of(postId))).thenReturn(post);

        setRole(ownerId, "user");

        PurgePostHandler handler = new PurgePostHandler(
                postRepository, postContentStore, eventPublisher, postCacheInvalidationStore, postReaderPresenceAppService);
        PurgePostCommand command = new PurgePostCommand(PostId.of(postId), UserId.of(ownerId));

        handler.handle(command);

        verify(postRepository).delete(PostId.of(postId));
        verify(postReaderPresenceAppService).evictPost(postId);
    }

    @Test
    void admin_canPurge_deletedPost_notOwner() {
        Long postId = 4L;
        Long ownerId = 10L;
        Long operatorId = 11L;

        Post post = buildPost(postId, ownerId, true);
        when(postRepository.load(PostId.of(postId))).thenReturn(post);

        setRole(operatorId, "admin");

        PurgePostHandler handler = new PurgePostHandler(
                postRepository, postContentStore, eventPublisher, postCacheInvalidationStore, postReaderPresenceAppService);
        PurgePostCommand command = new PurgePostCommand(PostId.of(postId), UserId.of(operatorId));

        handler.handle(command);

        verify(postRepository).delete(PostId.of(postId));
        verify(postReaderPresenceAppService).evictPost(postId);
    }

    @Test
    void nonAdmin_nonOwner_cannotPurge_deletedPost() {
        Long postId = 5L;
        Long ownerId = 10L;
        Long operatorId = 11L;

        Post post = buildPost(postId, ownerId, true);
        when(postRepository.load(PostId.of(postId))).thenReturn(post);

        setRole(operatorId, "user");

        PurgePostHandler handler = new PurgePostHandler(
                postRepository, postContentStore, eventPublisher, postCacheInvalidationStore, postReaderPresenceAppService);
        PurgePostCommand command = new PurgePostCommand(PostId.of(postId), UserId.of(operatorId));

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(PostOwnershipException.class);
    }

    private static Post buildPost(Long postId, Long ownerId, boolean deleted) {
        Post post = Post.createDraft(PostId.of(postId), UserId.of(ownerId), "t");
        if (deleted) {
            post.delete();
        }
        return post;
    }

    private static void setRole(Long userId, String role) {
        UserContext.UserInfo info = new UserContext.UserInfo(String.valueOf(userId), "u");
        info.setRole(role);
        UserContext.setUser(info);
    }
}
