package com.zhicore.content.application.service;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.content.application.dto.PostReaderPresenceSessionSnapshot;
import com.zhicore.content.application.dto.PostReaderPresenceView;
import com.zhicore.content.application.port.client.UserProfileClient;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostReaderPresenceStore;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostReaderPresenceAppServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostReaderPresenceStore postReaderPresenceStore;
    @Mock
    private UserProfileClient userProfileClient;
    @Mock
    private PostFileUrlResolver postFileUrlResolver;

    @InjectMocks
    private PostReaderPresenceAppService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("匿名用户注册时计数增加但不展示头像")
    void shouldCountAnonymousReaderWithoutAvatar() {
        when(postRepository.findById(1001L)).thenReturn(Optional.of(publishedPost(1001L)));
        when(postReaderPresenceStore.acquireAnonymousRegistration(eq(1001L), any(), any(), eq(8))).thenReturn(true);
        when(postReaderPresenceStore.acquireHeartbeatThrottle(eq(1001L), eq("session-a"), any())).thenReturn(true);
        when(postReaderPresenceStore.query(eq(1001L), any(Long.class), eq(3)))
                .thenReturn(PostReaderPresenceView.builder().readingCount(1).avatars(java.util.List.of()).build());

        PostReaderPresenceView view = service.register(1001L, "session-a", "ua", "127.0.0.1");

        ArgumentCaptor<PostReaderPresenceSessionSnapshot> captor = ArgumentCaptor.forClass(PostReaderPresenceSessionSnapshot.class);
        verify(postReaderPresenceStore).touchSession(captor.capture(), any());
        assertThat(captor.getValue().isAnonymous()).isTrue();
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(view.getReadingCount()).isEqualTo(1);
        assertThat(view.getAvatars()).isEmpty();
    }

    @Test
    @DisplayName("登录用户注册时应写入昵称和头像")
    void shouldAttachUserAvatarForAuthenticatedReader() {
        UserContext.UserInfo user = new UserContext.UserInfo("2001", "读者");
        UserContext.setUser(user);
        when(postRepository.findById(1001L)).thenReturn(Optional.of(publishedPost(1001L)));
        when(userProfileClient.getOwnerSnapshot(UserId.of(2001L)))
                .thenReturn(Optional.of(new OwnerSnapshot(UserId.of(2001L), "读者昵称", "avatar-file", 1L)));
        when(postFileUrlResolver.resolve("avatar-file")).thenReturn("https://cdn.example/avatar.png");
        when(postReaderPresenceStore.query(eq(1001L), any(Long.class), eq(3)))
                .thenReturn(PostReaderPresenceView.builder().readingCount(1).avatars(java.util.List.of()).build());

        service.register(1001L, "session-user", "ua", "127.0.0.1");

        ArgumentCaptor<PostReaderPresenceSessionSnapshot> captor = ArgumentCaptor.forClass(PostReaderPresenceSessionSnapshot.class);
        verify(postReaderPresenceStore).touchSession(captor.capture(), any());
        assertThat(captor.getValue().isAnonymous()).isFalse();
        assertThat(captor.getValue().getUserId()).isEqualTo(2001L);
        assertThat(captor.getValue().getNickname()).isEqualTo("读者昵称");
        assertThat(captor.getValue().getAvatarUrl()).isEqualTo("https://cdn.example/avatar.png");
        verify(postReaderPresenceStore, never()).acquireAnonymousRegistration(any(), any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("未发布文章不允许注册或查询")
    void shouldRejectInvisiblePost() {
        when(postRepository.findById(1001L)).thenReturn(Optional.of(draftPost(1001L)));

        assertThatThrownBy(() -> service.register(1001L, "session-a", "ua", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query(1001L))
                .isInstanceOf(BusinessException.class);
        verify(postReaderPresenceStore, never()).touchSession(any(), any());
    }

    private Post publishedPost(Long postId) {
        return Post.reconstitute(new Post.Snapshot(
                com.zhicore.content.domain.model.PostId.of(postId),
                UserId.of(1L),
                new OwnerSnapshot(UserId.of(1L), "作者", null, 1L),
                "标题",
                "摘要",
                null,
                PostStatus.PUBLISHED,
                null,
                java.util.Set.of(),
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                false,
                com.zhicore.content.domain.model.PostStats.empty(com.zhicore.content.domain.model.PostId.of(postId)),
                com.zhicore.content.domain.model.WriteState.NONE,
                null,
                1L
        ));
    }

    private Post draftPost(Long postId) {
        return Post.reconstitute(new Post.Snapshot(
                com.zhicore.content.domain.model.PostId.of(postId),
                UserId.of(1L),
                new OwnerSnapshot(UserId.of(1L), "作者", null, 1L),
                "标题",
                "摘要",
                null,
                PostStatus.DRAFT,
                null,
                java.util.Set.of(),
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                false,
                com.zhicore.content.domain.model.PostStats.empty(com.zhicore.content.domain.model.PostId.of(postId)),
                com.zhicore.content.domain.model.WriteState.NONE,
                null,
                1L
        ));
    }
}
