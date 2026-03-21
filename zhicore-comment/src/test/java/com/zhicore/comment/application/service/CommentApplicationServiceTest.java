package com.zhicore.comment.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.PostCommentClient;
import com.zhicore.api.client.UserSimpleBatchClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.comment.application.command.CreateCommentCommand;
import com.zhicore.comment.application.command.UpdateCommentCommand;
import com.zhicore.comment.application.port.event.CommentIntegrationEventPort;
import com.zhicore.comment.application.port.store.CommentCounterStore;
import com.zhicore.comment.application.port.store.CommentDetailCacheStore;
import com.zhicore.comment.application.service.command.CommentCommandService;
import com.zhicore.comment.application.service.query.CommentHomepageCacheService;
import com.zhicore.comment.application.service.query.CommentQueryService;
import com.zhicore.comment.application.service.query.CommentViewAssembler;
import com.zhicore.comment.domain.cursor.HotCursorCodec;
import com.zhicore.comment.domain.cursor.TimeCursorCodec;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.domain.repository.CommentStatsRepository;
import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.DomainException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.integration.messaging.comment.CommentCreatedIntegrationEvent;
import com.zhicore.integration.messaging.comment.CommentDeletedIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论 query/command 服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Comment query/command 服务单元测试")
class CommentApplicationServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentDetailCacheService commentDetailCacheService;
    @Mock private CommentDetailCacheStore commentDetailCacheStore;
    @Mock private CommentCounterStore commentCounterStore;
    @Mock private CommentStatsRepository commentStatsRepository;
    @Mock private CommentIntegrationEventPort eventPublisher;
    @Mock private PostCommentClient postServiceClient;
    @Mock private UserSimpleBatchClient userServiceClient;
    @Mock private IdGeneratorFeignClient idGeneratorFeignClient;
    @Mock private HotCursorCodec hotCursorCodec;
    @Mock private TimeCursorCodec timeCursorCodec;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private CommentViewAssembler commentViewAssembler;
    @Mock private CommentHomepageCacheService commentHomepageCacheService;

    private CommentCommandService commandService;
    private CommentQueryService queryService;

    private static final Long USER_ID = 1001L;
    private static final Long COMMENT_ID = 2001L;
    private static final Long POST_ID = 3001L;
    private static final Long POST_OWNER_ID = 5001L;

    @BeforeEach
    void setUp() {
        commandService = new CommentCommandService(
                commentRepository, commentDetailCacheStore, commentCounterStore,
                commentStatsRepository, eventPublisher, postServiceClient,
                idGeneratorFeignClient, transactionTemplate, new TransactionCommitSignal()
        );
        queryService = new CommentQueryService(
                commentRepository, commentDetailCacheService, commentViewAssembler, commentHomepageCacheService,
                hotCursorCodec, timeCursorCodec
        );

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0, java.util.function.Consumer.class);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private PostDTO createPostDTO() {
        PostDTO post = new PostDTO();
        post.setId(POST_ID);
        post.setOwnerId(POST_OWNER_ID);
        post.setTitle("测试文章");
        return post;
    }

    private CreateCommentCommand createTopLevelRequest() {
        return new CreateCommentCommand(POST_ID, "这是一条顶级评论", null, null, null, null, null);
    }

    private CreateCommentCommand createReplyRequest(Long rootId) {
        return new CreateCommentCommand(POST_ID, "这是一条回复", rootId, null, null, null, null);
    }

    private Comment createTopLevelComment() {
        return Comment.reconstitute(
                COMMENT_ID, POST_ID, USER_ID, "顶级评论",
                null, null, null,
                null, COMMENT_ID, null,
                CommentStatus.NORMAL, LocalDateTime.now(), LocalDateTime.now(),
                CommentStats.empty()
        );
    }

    private Comment createReplyComment(Long rootId) {
        return Comment.reconstitute(
                COMMENT_ID + 1, POST_ID, USER_ID, "回复评论",
                null, null, null,
                rootId, rootId, POST_OWNER_ID,
                CommentStatus.NORMAL, LocalDateTime.now(), LocalDateTime.now(),
                CommentStats.empty()
        );
    }

    private Comment createDeletedComment() {
        return Comment.reconstitute(
                COMMENT_ID, POST_ID, USER_ID, "已删除评论",
                null, null, null,
                null, COMMENT_ID, null,
                CommentStatus.DELETED, LocalDateTime.now(), LocalDateTime.now(),
                CommentStats.empty()
        );
    }

    @Nested
    @DisplayName("createComment 创建评论")
    class CreateCommentTest {

        @Test
        @DisplayName("创建顶级评论成功")
        void shouldCreateTopLevelComment() {
            when(postServiceClient.getPost(POST_ID)).thenReturn(ApiResponse.success(createPostDTO()));
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(COMMENT_ID));

            Long result = commandService.createComment(USER_ID, createTopLevelRequest());

            assertEquals(COMMENT_ID, result);
            verify(commentRepository).save(any(Comment.class));
            verify(eventPublisher).publish(any(CommentCreatedIntegrationEvent.class));
        }

        @Test
        @DisplayName("文章不存在时抛出异常")
        void shouldThrowWhenPostNotFound() {
            when(postServiceClient.getPost(POST_ID)).thenReturn(ApiResponse.fail("文章不存在"));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commandService.createComment(USER_ID, createTopLevelRequest()));
            assertEquals(ResultCode.POST_NOT_FOUND.getCode(), exception.getCode());
            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("ID 生成失败时抛出异常")
        void shouldThrowWhenIdGenerationFails() {
            when(postServiceClient.getPost(POST_ID)).thenReturn(ApiResponse.success(createPostDTO()));
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.fail("ID 生成失败"));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commandService.createComment(USER_ID, createTopLevelRequest()));
            assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
            assertEquals("评论ID生成失败", exception.getMessage());
        }

        @Test
        @DisplayName("创建回复评论成功，更新顶级评论回复数")
        void shouldCreateReplyAndIncrementReplyCount() {
            Long replyId = COMMENT_ID + 1;
            when(postServiceClient.getPost(POST_ID)).thenReturn(ApiResponse.success(createPostDTO()));
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(replyId));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));

            Long result = commandService.createComment(USER_ID, createReplyRequest(COMMENT_ID));

            assertEquals(replyId, result);
            verify(commentStatsRepository).incrementReplyCount(COMMENT_ID);
            verify(commentRepository).save(any(Comment.class));
        }

        @Test
        @DisplayName("回复已删除的评论时抛出异常")
        void shouldThrowWhenReplyToDeletedComment() {
            when(postServiceClient.getPost(POST_ID)).thenReturn(ApiResponse.success(createPostDTO()));
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(COMMENT_ID + 1));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createDeletedComment()));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commandService.createComment(USER_ID, createReplyRequest(COMMENT_ID)));
            assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
            assertEquals("不能回复已删除的评论", exception.getMessage());
        }

        @Test
        @DisplayName("根评论不存在时应该返回专属错误码")
        void shouldThrowWhenRootCommentNotFound() {
            when(postServiceClient.getPost(POST_ID)).thenReturn(ApiResponse.success(createPostDTO()));
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(COMMENT_ID + 1));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commandService.createComment(USER_ID, createReplyRequest(COMMENT_ID)));
            assertEquals(ResultCode.ROOT_COMMENT_NOT_FOUND.getCode(), exception.getCode());
            assertEquals(ResultCode.ROOT_COMMENT_NOT_FOUND.getMessage(), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("updateComment 更新评论")
    class UpdateCommentTest {

        @Test
        @DisplayName("非作者更新评论时应返回操作不允许错误码")
        void shouldThrowWhenUpdateByNonAuthor() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));

            DomainException exception = assertThrows(DomainException.class,
                    () -> commandService.updateComment(9999L, COMMENT_ID, new UpdateCommentCommand("更新后的内容")));

            assertEquals(ResultCode.OPERATION_NOT_ALLOWED.getCode(), exception.getCode());
            assertEquals("只能编辑自己的评论", exception.getMessage());
        }

        @Test
        @DisplayName("更新已删除评论时应返回评论已删除错误码")
        void shouldThrowWhenUpdateDeletedComment() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createDeletedComment()));

            DomainException exception = assertThrows(DomainException.class,
                    () -> commandService.updateComment(USER_ID, COMMENT_ID, new UpdateCommentCommand("更新后的内容")));

            assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
            assertEquals("已删除的评论不能编辑", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("分页查询")
    class PaginationQueryTest {

        @Test
        @DisplayName("传统分页超出查询窗口时应该拒绝并提示改用游标分页")
        void shouldRejectDeepOffsetPagination() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queryService.getTopLevelCommentsByPage(POST_ID, 50, 100, com.zhicore.comment.application.dto.CommentSortType.TIME));

            assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
            assertEquals("传统分页仅支持前" + CommonConstants.MAX_OFFSET_WINDOW + "条数据，请改用游标分页", exception.getMessage());
            verify(commentRepository, never()).findTopLevelByPostIdOrderByTimePage(anyLong(), anyInt(), anyInt());
            verify(commentRepository, never()).findTopLevelByPostIdOrderByLikesPage(anyLong(), anyInt(), anyInt());
        }

    }

    @Nested
    @DisplayName("deleteComment 删除评论")
    class DeleteCommentTest {

        @Test
        @DisplayName("作者删除顶级评论成功，发布 CommentDeletedEvent")
        void shouldDeleteTopLevelAndPublishEvent() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));

            commandService.deleteComment(USER_ID, false, COMMENT_ID);

            verify(commentRepository).update(any(Comment.class));
            verify(eventPublisher).publish(argThat(event ->
                    event instanceof CommentDeletedIntegrationEvent deleted
                            && deleted.getCommentId().equals(COMMENT_ID)
                            && deleted.isTopLevel()
                            && "AUTHOR".equals(deleted.getDeletedBy())
            ));
        }

        @Test
        @DisplayName("删除回复评论时递减顶级评论回复数")
        void shouldDecrementReplyCountWhenDeletingReply() {
            Comment reply = createReplyComment(COMMENT_ID);
            when(commentRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

            commandService.deleteComment(USER_ID, false, reply.getId());

            verify(commentStatsRepository).decrementReplyCount(COMMENT_ID);
        }

        @Test
        @DisplayName("评论不存在时抛出异常")
        void shouldThrowWhenCommentNotFound() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commandService.deleteComment(USER_ID, false, COMMENT_ID));
            assertEquals(ResultCode.COMMENT_NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("非作者删除评论时应返回操作不允许错误码")
        void shouldThrowWhenDeleteByNonAuthor() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));

            DomainException exception = assertThrows(DomainException.class,
                    () -> commandService.deleteComment(9999L, false, COMMENT_ID));

            assertEquals(ResultCode.OPERATION_NOT_ALLOWED.getCode(), exception.getCode());
            assertEquals("无权删除此评论", exception.getMessage());
        }

        @Test
        @DisplayName("删除已删除评论时应返回评论已删除错误码")
        void shouldThrowWhenDeleteDeletedComment() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createDeletedComment()));

            DomainException exception = assertThrows(DomainException.class,
                    () -> commandService.deleteComment(USER_ID, false, COMMENT_ID));

            assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
            assertEquals("评论已经删除", exception.getMessage());
        }

        @Test
        @DisplayName("管理员删除评论，deletedBy=ADMIN")
        void shouldSetDeletedByAdminWhenAdminDeletes() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));

            commandService.deleteComment(9999L, true, COMMENT_ID);

            verify(eventPublisher).publish(argThat(event ->
                    event instanceof CommentDeletedIntegrationEvent deleted
                            && "ADMIN".equals(deleted.getDeletedBy())
            ));
        }
    }

    @Nested
    @DisplayName("getComment 获取评论详情")
    class GetCommentTest {

        @Test
        @DisplayName("获取评论详情成功")
        void shouldReturnCommentVO() {
            UserSimpleDTO user = new UserSimpleDTO();
            user.setId(USER_ID);
            user.setNickname("测试用户");
            when(commentDetailCacheService.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));
            when(commentViewAssembler.assembleCommentVO(any(Comment.class)))
                    .thenReturn(com.zhicore.comment.application.dto.CommentVO.builder()
                            .id(COMMENT_ID)
                            .content("顶级评论")
                            .author(user)
                            .build());

            var vo = queryService.getComment(COMMENT_ID);

            assertNotNull(vo);
            assertEquals(COMMENT_ID, vo.getId());
            assertEquals("顶级评论", vo.getContent());
        }

        @Test
        @DisplayName("用户服务失败时不返回伪造作者")
        void shouldNotFabricateAuthorWhenUserServiceUnavailable() {
            when(commentDetailCacheService.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));
            when(commentViewAssembler.assembleCommentVO(any(Comment.class)))
                    .thenReturn(com.zhicore.comment.application.dto.CommentVO.builder()
                            .id(COMMENT_ID)
                            .content("顶级评论")
                            .author(null)
                            .build());

            var vo = queryService.getComment(COMMENT_ID);

            assertNotNull(vo);
            assertNull(vo.getAuthor());
        }

        @Test
        @DisplayName("评论不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(commentDetailCacheService.findById(COMMENT_ID)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> queryService.getComment(COMMENT_ID));
        }
    }
}
