package com.zhicore.content.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnedPostLoadServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private OwnedPostLoadService ownedPostLoadService;

    @Test
    void shouldRejectWhenPostNotFound() {
        when(postRepository.findById(2001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ownedPostLoadService.load(2001L, 1001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文章不存在");
    }

    @Test
    void shouldRejectWhenPostOwnedByAnotherUser() {
        Post post = Post.createDraft(PostId.of(2001L), UserId.of(3001L), "title");
        when(postRepository.findById(2001L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> ownedPostLoadService.load(2001L, 1001L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作此文章");
    }
}
