package com.zhicore.comment.application.service.query;

import com.zhicore.api.client.UserSimpleBatchClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.result.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentViewAssembler Tests")
class CommentViewAssemblerTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserSimpleBatchClient userSimpleBatchClient;

    @Test
    @DisplayName("预加载热门回复时应使用配置的 hotRepliesLimit")
    void shouldUseConfiguredHotRepliesLimit() {
        CommentViewAssembler assembler = new CommentViewAssembler(commentRepository, userSimpleBatchClient, 5);

        Comment topLevel = Comment.reconstitute(
                2001L, 3001L, 1001L, "顶级评论",
                null, null, null,
                null, 2001L, null,
                CommentStatus.NORMAL, LocalDateTime.now(), LocalDateTime.now(),
                CommentStats.empty()
        );
        UserSimpleDTO user = new UserSimpleDTO();
        user.setId(1001L);

        when(userSimpleBatchClient.batchGetUsersSimple(Set.of(1001L))).thenReturn(ApiResponse.success(Map.of(1001L, user)));
        when(commentRepository.batchGetStats(List.of(2001L))).thenReturn(Map.of(2001L, CommentStats.empty()));
        when(commentRepository.findHotRepliesByRootId(2001L, 5)).thenReturn(List.of());

        var result = assembler.assembleCommentVOList(List.of(topLevel));

        assertThat(result).hasSize(1);
        verify(commentRepository).findHotRepliesByRootId(2001L, 5);
    }
}
