package com.zhicore.comment.application.service;

import com.zhicore.comment.application.port.store.CommentLikeStore;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentLikeRepository;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.domain.repository.CommentStatsRepository;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论点赞 query/command 服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CommentLike query/command 服务单元测试")
class CommentLikeApplicationServiceTest {

    @Mock private CommentLikeRepository likeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentStatsRepository commentStatsRepository;
    @Mock private CommentLikeStore commentLikeStore;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private CommentLikeCommandService commandService;
    private CommentLikeQueryService queryService;

    private static final Long USER_ID = 1001L;
    private static final Long COMMENT_ID = 2001L;
    private static final Long POST_ID = 3001L;
    private static final Long AUTHOR_ID = 4001L;

    @BeforeEach
    void setUp() {
        commandService = new CommentLikeCommandService(
                likeRepository, commentRepository, commentStatsRepository,
                commentLikeStore, transactionTemplate, meterRegistry
        );
        queryService = new CommentLikeQueryService(likeRepository, commentLikeStore);

        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0, java.util.function.Consumer.class);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Comment createNormalComment() {
        return Comment.reconstitute(
                COMMENT_ID, POST_ID, AUTHOR_ID, "测试评论内容",
                null, null, null,
                null, COMMENT_ID, null,
                CommentStatus.NORMAL, LocalDateTime.now(), LocalDateTime.now(),
                CommentStats.empty()
        );
    }

    private Comment createDeletedComment() {
        return Comment.reconstitute(
                COMMENT_ID, POST_ID, AUTHOR_ID, "已删除评论",
                null, null, null,
                null, COMMENT_ID, null,
                CommentStatus.DELETED, LocalDateTime.now(), LocalDateTime.now(),
                CommentStats.empty()
        );
    }

    @Nested
    @DisplayName("likeComment 点赞")
    class LikeCommentTest {

        @Test
        @DisplayName("首次点赞成功：实际插入，计数+1，Redis 更新")
        void shouldLikeSuccessfullyWhenFirstTime() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createNormalComment()));
            when(likeRepository.insertIfAbsent(COMMENT_ID, USER_ID)).thenReturn(true);

            commandService.likeComment(USER_ID, COMMENT_ID);

            verify(likeRepository).insertIfAbsent(COMMENT_ID, USER_ID);
            verify(commentStatsRepository).incrementLikeCount(COMMENT_ID);
            verify(commentLikeStore).incrementLikeCount(COMMENT_ID);
            verify(commentLikeStore).markLiked(USER_ID, COMMENT_ID);
        }

        @Test
        @DisplayName("重复点赞幂等：ON CONFLICT DO NOTHING，计数不变")
        void shouldBeIdempotentWhenAlreadyLiked() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createNormalComment()));
            when(likeRepository.insertIfAbsent(COMMENT_ID, USER_ID)).thenReturn(false);

            commandService.likeComment(USER_ID, COMMENT_ID);

            verify(commentStatsRepository, never()).incrementLikeCount(anyLong());
            verify(commentLikeStore, never()).incrementLikeCount(anyLong());
            verify(commentLikeStore).markLiked(USER_ID, COMMENT_ID);
        }

        @Test
        @DisplayName("评论不存在时抛出异常")
        void shouldThrowWhenCommentNotFound() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commandService.likeComment(USER_ID, COMMENT_ID));
            assertEquals(ResultCode.COMMENT_NOT_FOUND.getCode(), exception.getCode());
            verify(likeRepository, never()).insertIfAbsent(anyLong(), anyLong());
        }

        @Test
        @DisplayName("评论已删除时抛出异常")
        void shouldThrowWhenCommentDeleted() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createDeletedComment()));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> commandService.likeComment(USER_ID, COMMENT_ID));
            assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
            assertEquals("评论已删除，无法点赞", exception.getMessage());
            verify(likeRepository, never()).insertIfAbsent(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Redis 缓存更新失败不影响业务")
        void shouldSucceedWhenRedisUpdateFails() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createNormalComment()));
            when(likeRepository.insertIfAbsent(COMMENT_ID, USER_ID)).thenReturn(true);
            doThrow(new RuntimeException("Redis down")).when(commentLikeStore).incrementLikeCount(anyLong());

            assertDoesNotThrow(() -> commandService.likeComment(USER_ID, COMMENT_ID));
            verify(commentStatsRepository).incrementLikeCount(COMMENT_ID);
        }
    }

    @Nested
    @DisplayName("unlikeComment 取消点赞")
    class UnlikeCommentTest {

        @Test
        @DisplayName("取消点赞成功：实际删除，计数-1，Redis 更新")
        void shouldUnlikeSuccessfully() {
            when(likeRepository.deleteAndReturnAffected(COMMENT_ID, USER_ID)).thenReturn(true);

            commandService.unlikeComment(USER_ID, COMMENT_ID);

            verify(commentStatsRepository).decrementLikeCount(COMMENT_ID);
            verify(commentLikeStore).decrementLikeCount(COMMENT_ID);
            verify(commentLikeStore).unmarkLiked(USER_ID, COMMENT_ID);
        }

        @Test
        @DisplayName("未点赞时取消幂等：affected_rows=0，计数不变")
        void shouldBeIdempotentWhenNotLiked() {
            when(likeRepository.deleteAndReturnAffected(COMMENT_ID, USER_ID)).thenReturn(false);

            commandService.unlikeComment(USER_ID, COMMENT_ID);

            verify(commentStatsRepository, never()).decrementLikeCount(anyLong());
            verify(commentLikeStore, never()).decrementLikeCount(anyLong());
            verify(commentLikeStore).unmarkLiked(USER_ID, COMMENT_ID);
        }

        @Test
        @DisplayName("Redis 缓存更新失败不影响业务")
        void shouldSucceedWhenRedisUpdateFails() {
            when(likeRepository.deleteAndReturnAffected(COMMENT_ID, USER_ID)).thenReturn(true);
            doThrow(new RuntimeException("Redis down")).when(commentLikeStore).decrementLikeCount(anyLong());

            assertDoesNotThrow(() -> commandService.unlikeComment(USER_ID, COMMENT_ID));
            verify(commentStatsRepository).decrementLikeCount(COMMENT_ID);
        }
    }

    @Nested
    @DisplayName("isLiked / getLikeCount 查询")
    class QueryTest {

        @Test
        @DisplayName("Redis 命中时直接返回 true")
        void isLikedShouldReturnTrueWhenRedisHit() {
            when(commentLikeStore.isLiked(USER_ID, COMMENT_ID)).thenReturn(true);

            assertTrue(queryService.isLiked(USER_ID, COMMENT_ID));
            verify(likeRepository, never()).exists(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Redis 未命中时查 DB 并回填")
        void isLikedShouldQueryDBWhenRedisMiss() {
            when(commentLikeStore.isLiked(USER_ID, COMMENT_ID)).thenReturn(null);
            when(likeRepository.exists(COMMENT_ID, USER_ID)).thenReturn(true);

            assertTrue(queryService.isLiked(USER_ID, COMMENT_ID));
            verify(commentLikeStore).markLiked(USER_ID, COMMENT_ID);
        }

        @Test
        @DisplayName("getLikeCount Redis 命中")
        void getLikeCountShouldReturnFromRedis() {
            when(commentLikeStore.getLikeCount(COMMENT_ID)).thenReturn(42);

            assertEquals(42, queryService.getLikeCount(COMMENT_ID));
            verify(likeRepository, never()).countByCommentId(anyLong());
        }

        @Test
        @DisplayName("getLikeCount Redis 未命中时查 DB 并回填")
        void getLikeCountShouldQueryDBWhenRedisMiss() {
            when(commentLikeStore.getLikeCount(COMMENT_ID)).thenReturn(null);
            when(likeRepository.countByCommentId(COMMENT_ID)).thenReturn(10);

            assertEquals(10, queryService.getLikeCount(COMMENT_ID));
            verify(commentLikeStore).cacheLikeCount(COMMENT_ID, 10);
        }

        @Test
        @DisplayName("batchCheckLiked Redis 命中与 DB 回填应正确合并")
        void batchCheckLikedShouldMergeCacheHitsAndDbResults() {
            List<Long> commentIds = List.of(COMMENT_ID, COMMENT_ID + 1, COMMENT_ID + 2);
            when(commentLikeStore.findLikedCommentIds(USER_ID, commentIds)).thenReturn(Set.of(COMMENT_ID));
            when(likeRepository.findLikedCommentIds(USER_ID, List.of(COMMENT_ID + 1, COMMENT_ID + 2)))
                    .thenReturn(List.of(COMMENT_ID + 2));

            var result = queryService.batchCheckLiked(USER_ID, commentIds);

            assertEquals(Boolean.TRUE, result.get(COMMENT_ID));
            assertEquals(Boolean.FALSE, result.get(COMMENT_ID + 1));
            assertEquals(Boolean.TRUE, result.get(COMMENT_ID + 2));
            verify(commentLikeStore).markLiked(USER_ID, COMMENT_ID + 2);
        }
    }
}
