package com.zhicore.comment.application.service;

import com.zhicore.api.client.PostBatchClient;
import com.zhicore.api.client.UserBatchSimpleClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.comment.application.service.query.AdminCommentQueryService;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCommentQueryService 单元测试")
class AdminCommentApplicationServiceTest {

    private static final Long COMMENT_ID = 1001L;
    private static final Long POST_ID = 2001L;
    private static final Long AUTHOR_ID = 3001L;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserBatchSimpleClient userServiceClient;

    @Mock
    private PostBatchClient postServiceClient;

    private AdminCommentQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminCommentQueryService(
                commentRepository,
                userServiceClient,
                postServiceClient
        );
    }

    @Test
    @DisplayName("用户服务降级时应显式失败")
    void shouldFailWhenUserServiceDegraded() {
        when(commentRepository.findByConditions(null, null, null, 0, 20))
                .thenReturn(List.of(createComment()));
        when(commentRepository.countByConditions(null, null, null)).thenReturn(1L);
        when(userServiceClient.batchGetUsersSimple(anySet()))
                .thenReturn(ApiResponse.fail(ResultCode.SERVICE_DEGRADED, "用户服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.queryComments(null, null, null, 1, 20));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("用户服务已降级", exception.getMessage());
        verify(postServiceClient, never()).batchGetPosts(anySet());
    }

    @Test
    @DisplayName("文章服务降级时应显式失败")
    void shouldFailWhenPostServiceDegraded() {
        when(commentRepository.findByConditions(null, null, null, 0, 20))
                .thenReturn(List.of(createComment()));
        when(commentRepository.countByConditions(null, null, null)).thenReturn(1L);
        when(userServiceClient.batchGetUsersSimple(anySet()))
                .thenReturn(ApiResponse.success(Map.of(AUTHOR_ID, createUser())));
        when(postServiceClient.batchGetPosts(anySet()))
                .thenReturn(ApiResponse.fail(ResultCode.SERVICE_DEGRADED, "文章服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.queryComments(null, null, null, 1, 20));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("文章服务已降级", exception.getMessage());
    }

    private Comment createComment() {
        return Comment.reconstitute(
                COMMENT_ID,
                POST_ID,
                AUTHOR_ID,
                "测试评论",
                null,
                null,
                null,
                null,
                COMMENT_ID,
                null,
                CommentStatus.NORMAL,
                LocalDateTime.now(),
                LocalDateTime.now(),
                CommentStats.empty()
        );
    }

    private UserSimpleDTO createUser() {
        UserSimpleDTO user = new UserSimpleDTO();
        user.setId(AUTHOR_ID);
        user.setNickname("tester");
        return user;
    }
}
