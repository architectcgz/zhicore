package com.zhicore.comment.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.api.event.comment.CommentCreatedEvent;
import com.zhicore.api.event.comment.CommentDeletedEvent;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cache.CommentRedisKeys;
import com.zhicore.comment.infrastructure.cursor.HotCursorCodec;
import com.zhicore.comment.infrastructure.cursor.TimeCursorCodec;
import com.zhicore.comment.infrastructure.feign.PostServiceClient;
import com.zhicore.comment.infrastructure.feign.UserServiceClient;
import com.zhicore.comment.infrastructure.mq.CommentEventPublisher;
import com.zhicore.comment.infrastructure.repository.mapper.CommentStatsMapper;
import com.zhicore.comment.interfaces.dto.request.CreateCommentRequest;
import com.zhicore.comment.interfaces.dto.request.UpdateCommentRequest;
import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.DomainException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 评论应用服务单元测试
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CommentApplicationService 单元测试")
class CommentApplicationServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentStatsMapper statsMapper;
    @Mock private CommentEventPublisher eventPublisher;
    @Mock private PostServiceClient postServiceClient;
    @Mock private UserServiceClient userServiceClient;
    @Mock private IdGeneratorFeignClient idGeneratorFeignClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private HotCursorCodec hotCursorCodec;
    @Mock private TimeCursorCodec timeCursorCodec;
    @Mock private TransactionTemplate transactionTemplate;

    private CommentApplicationService service;

    private static final Long USER_ID = 1001L;
    private static final Long COMMENT_ID = 2001L;
    private static final Long POST_ID = 3001L;
    private static final Long POST_OWNER_ID = 5001L;

    @BeforeEach
    void setUp() {
        service = new CommentApplicationService(
                commentRepository, statsMapper, eventPublisher,
                postServiceClient, userServiceClient, idGeneratorFeignClient,
                redisTemplate, hotCursorCodec, timeCursorCodec, transactionTemplate
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // TransactionTemplate 默认直接执行回调
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0, java.util.function.Consumer.class);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    // ==================== 辅助方法 ====================

    private PostDTO createPostDTO() {
        PostDTO post = new PostDTO();
        post.setId(POST_ID);
        post.setOwnerId(POST_OWNER_ID);
        post.setTitle("测试文章");
        return post;
    }

    private CreateCommentRequest createTopLevelRequest() {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setPostId(POST_ID);
        req.setContent("这是一条顶级评论");
        return req;
    }

    private CreateCommentRequest createReplyRequest(Long rootId) {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setPostId(POST_ID);
        req.setContent("这是一条回复");
        req.setRootId(rootId);
        return req;
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

    // ==================== 创建评论测试 ====================

    @Nested
    @DisplayName("createComment 创建评论")
    class CreateCommentTest {

        @Test
        @DisplayName("创建顶级评论成功")
        void shouldCreateTopLevelComment() {
            ApiResponse<PostDTO> postResp = ApiResponse.success(createPostDTO());
            when(postServiceClient.getPost(POST_ID)).thenReturn(postResp);

            ApiResponse<Long> idResp = ApiResponse.success(COMMENT_ID);
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(idResp);

            CreateCommentRequest request = createTopLevelRequest();
            Long result = service.createComment(USER_ID, request);

            assertEquals(COMMENT_ID, result);
            verify(commentRepository).save(any(Comment.class));
            verify(eventPublisher).publish(any(CommentCreatedEvent.class));
        }

        @Test
        @DisplayName("文章不存在时抛出异常")
        void shouldThrow_WhenPostNotFound() {
            ApiResponse<PostDTO> postResp = ApiResponse.fail("文章不存在");
            when(postServiceClient.getPost(POST_ID)).thenReturn(postResp);

            CreateCommentRequest request = createTopLevelRequest();
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.createComment(USER_ID, request));
            assertEquals(ResultCode.POST_NOT_FOUND.getCode(), exception.getCode());
            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("ID 生成失败时抛出异常")
        void shouldThrow_WhenIdGenerationFails() {
            ApiResponse<PostDTO> postResp = ApiResponse.success(createPostDTO());
            when(postServiceClient.getPost(POST_ID)).thenReturn(postResp);

            ApiResponse<Long> idResp = ApiResponse.fail("ID 生成失败");
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(idResp);

            CreateCommentRequest request = createTopLevelRequest();
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.createComment(USER_ID, request));
            assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
            assertEquals("评论ID生成失败", exception.getMessage());
        }

        @Test
        @DisplayName("创建回复评论成功，更新顶级评论回复数")
        void shouldCreateReply_AndIncrementReplyCount() {
            ApiResponse<PostDTO> postResp = ApiResponse.success(createPostDTO());
            when(postServiceClient.getPost(POST_ID)).thenReturn(postResp);

            Long replyId = COMMENT_ID + 1;
            ApiResponse<Long> idResp = ApiResponse.success(replyId);
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(idResp);

            Comment rootComment = createTopLevelComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(rootComment));

            CreateCommentRequest request = createReplyRequest(COMMENT_ID);
            Long result = service.createComment(USER_ID, request);

            assertEquals(replyId, result);
            verify(statsMapper).incrementReplyCount(COMMENT_ID);
            verify(commentRepository).save(any(Comment.class));
        }

        @Test
        @DisplayName("回复已删除的评论时抛出异常")
        void shouldThrow_WhenReplyToDeletedComment() {
            ApiResponse<PostDTO> postResp = ApiResponse.success(createPostDTO());
            when(postServiceClient.getPost(POST_ID)).thenReturn(postResp);

            ApiResponse<Long> idResp = ApiResponse.success(COMMENT_ID + 1);
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(idResp);

            when(commentRepository.findById(COMMENT_ID))
                    .thenReturn(Optional.of(createDeletedComment()));

            CreateCommentRequest request = createReplyRequest(COMMENT_ID);
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.createComment(USER_ID, request));
            assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
            assertEquals("不能回复已删除的评论", exception.getMessage());
        }

        @Test
        @DisplayName("根评论不存在时应该返回专属错误码")
        void shouldThrow_WhenRootCommentNotFound() {
            when(postServiceClient.getPost(POST_ID)).thenReturn(ApiResponse.success(createPostDTO()));
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(COMMENT_ID + 1));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.createComment(USER_ID, createReplyRequest(COMMENT_ID)));

            assertEquals(ResultCode.ROOT_COMMENT_NOT_FOUND.getCode(), exception.getCode());
            assertEquals(ResultCode.ROOT_COMMENT_NOT_FOUND.getMessage(), exception.getMessage());
        }
    }

    // ==================== 删除评论测试 ====================

    @Nested
    @DisplayName("updateComment 更新评论")
    class UpdateCommentTest {

        @Test
        @DisplayName("非作者更新评论时应返回操作不允许错误码")
        void shouldThrow_WhenUpdateByNonAuthor() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(9999L);

                Comment comment = createTopLevelComment();
                when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

                UpdateCommentRequest request = new UpdateCommentRequest();
                request.setContent("更新后的内容");

                DomainException exception = assertThrows(DomainException.class,
                        () -> service.updateComment(COMMENT_ID, request));

                assertEquals(ResultCode.OPERATION_NOT_ALLOWED.getCode(), exception.getCode());
                assertEquals("只能编辑自己的评论", exception.getMessage());
            }
        }

        @Test
        @DisplayName("更新已删除评论时应返回评论已删除错误码")
        void shouldThrow_WhenUpdateDeletedComment() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(USER_ID);

                when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createDeletedComment()));

                UpdateCommentRequest request = new UpdateCommentRequest();
                request.setContent("更新后的内容");

                DomainException exception = assertThrows(DomainException.class,
                        () -> service.updateComment(COMMENT_ID, request));

                assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
                assertEquals("已删除的评论不能编辑", exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("分页查询")
    class PaginationQueryTest {

        @Test
        @DisplayName("传统分页超出查询窗口时应该拒绝并提示改用游标分页")
        void shouldRejectDeepOffsetPagination() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.getTopLevelCommentsByPage(POST_ID, 50, 100, com.zhicore.comment.application.dto.CommentSortType.TIME));

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
        void shouldDeleteTopLevel_AndPublishEvent() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(USER_ID);
                uc.when(UserContext::isAdmin).thenReturn(false);

                Comment comment = createTopLevelComment();
                when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

                service.deleteComment(COMMENT_ID);

                verify(commentRepository).update(any(Comment.class));
                verify(eventPublisher).publishCommentDeleted(argThat(event ->
                        event.getCommentId().equals(COMMENT_ID)
                                && event.isTopLevel()
                                && "AUTHOR".equals(event.getDeletedBy())
                ));
            }
        }

        @Test
        @DisplayName("删除回复评论时递减顶级评论回复数")
        void shouldDecrementReplyCount_WhenDeletingReply() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(USER_ID);
                uc.when(UserContext::isAdmin).thenReturn(false);

                Comment reply = createReplyComment(COMMENT_ID);
                when(commentRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

                service.deleteComment(reply.getId());

                verify(statsMapper).decrementReplyCount(COMMENT_ID);
            }
        }

        @Test
        @DisplayName("评论不存在时抛出异常")
        void shouldThrow_WhenCommentNotFound() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(USER_ID);
                uc.when(UserContext::isAdmin).thenReturn(false);

                when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

                BusinessException exception = assertThrows(BusinessException.class,
                        () -> service.deleteComment(COMMENT_ID));
                assertEquals(ResultCode.COMMENT_NOT_FOUND.getCode(), exception.getCode());
            }
        }

        @Test
        @DisplayName("非作者删除评论时应返回操作不允许错误码")
        void shouldThrow_WhenDeleteByNonAuthor() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(9999L);
                uc.when(UserContext::isAdmin).thenReturn(false);

                when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createTopLevelComment()));

                DomainException exception = assertThrows(DomainException.class,
                        () -> service.deleteComment(COMMENT_ID));

                assertEquals(ResultCode.OPERATION_NOT_ALLOWED.getCode(), exception.getCode());
                assertEquals("无权删除此评论", exception.getMessage());
            }
        }

        @Test
        @DisplayName("删除已删除评论时应返回评论已删除错误码")
        void shouldThrow_WhenDeleteDeletedComment() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(USER_ID);
                uc.when(UserContext::isAdmin).thenReturn(false);

                when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createDeletedComment()));

                DomainException exception = assertThrows(DomainException.class,
                        () -> service.deleteComment(COMMENT_ID));

                assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
                assertEquals("评论已经删除", exception.getMessage());
            }
        }

        @Test
        @DisplayName("管理员删除评论，deletedBy=ADMIN")
        void shouldSetDeletedByAdmin_WhenAdminDeletes() {
            try (MockedStatic<UserContext> uc = mockStatic(UserContext.class)) {
                uc.when(UserContext::requireUserId).thenReturn(9999L); // 非作者
                uc.when(UserContext::isAdmin).thenReturn(true);

                Comment comment = createTopLevelComment();
                when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

                service.deleteComment(COMMENT_ID);

                verify(eventPublisher).publishCommentDeleted(argThat(event ->
                        "ADMIN".equals(event.getDeletedBy())
                ));
            }
        }
    }

    // ==================== 获取评论详情测试 ====================

    @Nested
    @DisplayName("getComment 获取评论详情")
    class GetCommentTest {

        @Test
        @DisplayName("获取评论详情成功")
        void shouldReturnCommentVO() {
            Comment comment = createTopLevelComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

            UserSimpleDTO user = new UserSimpleDTO();
            user.setId(USER_ID);
            user.setNickname("测试用户");
            ApiResponse<UserSimpleDTO> userResp = ApiResponse.success(user);
            when(userServiceClient.getUserSimple(USER_ID)).thenReturn(userResp);

            var vo = service.getComment(COMMENT_ID);

            assertNotNull(vo);
            assertEquals(COMMENT_ID, vo.getId());
            assertEquals("顶级评论", vo.getContent());
        }

        @Test
        @DisplayName("用户服务失败时不返回伪造作者")
        void shouldNotFabricateAuthorWhenUserServiceUnavailable() {
            Comment comment = createTopLevelComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(userServiceClient.getUserSimple(USER_ID))
                    .thenReturn(ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "用户服务暂时不可用"));

            var vo = service.getComment(COMMENT_ID);

            assertNotNull(vo);
            assertNull(vo.getAuthor());
        }

        @Test
        @DisplayName("评论不存在时抛出异常")
        void shouldThrow_WhenNotFound() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> service.getComment(COMMENT_ID));
        }
    }
}
