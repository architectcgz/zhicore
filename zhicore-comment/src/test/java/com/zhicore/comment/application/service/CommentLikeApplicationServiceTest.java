package com.zhicore.comment.application.service;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentLikeRepository;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cache.CommentRedisKeys;
import com.zhicore.comment.infrastructure.repository.mapper.CommentStatsMapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 评论点赞应用服务单元测试
 *
 * 覆盖场景：
 * 1. 点赞幂等（ON CONFLICT DO NOTHING）
 * 2. 取消点赞幂等（affected_rows 判断）
 * 3. 评论不存在 / 已删除时的异常
 * 4. Redis 缓存更新失败不影响业务
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CommentLikeApplicationService 单元测试")
class CommentLikeApplicationServiceTest {

    @Mock private CommentLikeRepository likeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentStatsMapper statsMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private CommentLikeApplicationService service;

    // 测试数据
    private static final Long USER_ID = 1001L;
    private static final Long COMMENT_ID = 2001L;
    private static final Long POST_ID = 3001L;
    private static final Long AUTHOR_ID = 4001L;

    @BeforeEach
    void setUp() {
        service = new CommentLikeApplicationService(
                likeRepository, commentRepository, statsMapper,
                redisTemplate, transactionTemplate, meterRegistry
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        // TransactionTemplate 默认直接执行回调
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

    // ==================== 点赞测试 ====================

    @Nested
    @DisplayName("likeComment 点赞")
    class LikeCommentTest {

        @Test
        @DisplayName("首次点赞成功：实际插入，计数+1，Redis 更新")
        void shouldLikeSuccessfully_WhenFirstTime() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createNormalComment()));
            when(likeRepository.insertIfAbsent(COMMENT_ID, USER_ID)).thenReturn(true);

            service.likeComment(USER_ID, COMMENT_ID);

            verify(likeRepository).insertIfAbsent(COMMENT_ID, USER_ID);
            verify(statsMapper).incrementLikeCount(COMMENT_ID);
            verify(valueOperations).increment(CommentRedisKeys.likeCount(COMMENT_ID));
            verify(valueOperations).set(CommentRedisKeys.userLiked(USER_ID, COMMENT_ID), "1");
        }

        @Test
        @DisplayName("重复点赞幂等：ON CONFLICT DO NOTHING，计数不变")
        void shouldBeIdempotent_WhenAlreadyLiked() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createNormalComment()));
            when(likeRepository.insertIfAbsent(COMMENT_ID, USER_ID)).thenReturn(false);

            service.likeComment(USER_ID, COMMENT_ID);

            verify(likeRepository).insertIfAbsent(COMMENT_ID, USER_ID);
            verify(statsMapper, never()).incrementLikeCount(anyLong());
            verify(valueOperations, never()).increment(anyString());
            // 仍然设置 Redis 标记（确保缓存一致）
            verify(valueOperations).set(CommentRedisKeys.userLiked(USER_ID, COMMENT_ID), "1");
        }

        @Test
        @DisplayName("评论不存在时抛出异常")
        void shouldThrow_WhenCommentNotFound() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.likeComment(USER_ID, COMMENT_ID));
            assertEquals(ResultCode.COMMENT_NOT_FOUND.getCode(), exception.getCode());
            verify(likeRepository, never()).insertIfAbsent(anyLong(), anyLong());
        }

        @Test
        @DisplayName("评论已删除时抛出异常")
        void shouldThrow_WhenCommentDeleted() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createDeletedComment()));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.likeComment(USER_ID, COMMENT_ID));
            assertEquals(ResultCode.COMMENT_ALREADY_DELETED.getCode(), exception.getCode());
            assertEquals("评论已删除，无法点赞", exception.getMessage());
            verify(likeRepository, never()).insertIfAbsent(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Redis 缓存更新失败不影响业务")
        void shouldSucceed_WhenRedisUpdateFails() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(createNormalComment()));
            when(likeRepository.insertIfAbsent(COMMENT_ID, USER_ID)).thenReturn(true);
            when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis down"));

            assertDoesNotThrow(() -> service.likeComment(USER_ID, COMMENT_ID));
            verify(statsMapper).incrementLikeCount(COMMENT_ID);
        }
    }

    // ==================== 取消点赞测试 ====================

    @Nested
    @DisplayName("unlikeComment 取消点赞")
    class UnlikeCommentTest {

        @Test
        @DisplayName("取消点赞成功：实际删除，计数-1，Redis 更新")
        void shouldUnlikeSuccessfully() {
            when(likeRepository.deleteAndReturnAffected(COMMENT_ID, USER_ID)).thenReturn(true);

            service.unlikeComment(USER_ID, COMMENT_ID);

            verify(likeRepository).deleteAndReturnAffected(COMMENT_ID, USER_ID);
            verify(statsMapper).decrementLikeCount(COMMENT_ID);
            verify(valueOperations).decrement(CommentRedisKeys.likeCount(COMMENT_ID));
            verify(redisTemplate).delete(CommentRedisKeys.userLiked(USER_ID, COMMENT_ID));
        }

        @Test
        @DisplayName("未点赞时取消幂等：affected_rows=0，计数不变")
        void shouldBeIdempotent_WhenNotLiked() {
            when(likeRepository.deleteAndReturnAffected(COMMENT_ID, USER_ID)).thenReturn(false);

            service.unlikeComment(USER_ID, COMMENT_ID);

            verify(statsMapper, never()).decrementLikeCount(anyLong());
            verify(valueOperations, never()).decrement(anyString());
            // 仍然清除 Redis 标记（确保缓存一致）
            verify(redisTemplate).delete(CommentRedisKeys.userLiked(USER_ID, COMMENT_ID));
        }

        @Test
        @DisplayName("Redis 缓存更新失败不影响业务")
        void shouldSucceed_WhenRedisUpdateFails() {
            when(likeRepository.deleteAndReturnAffected(COMMENT_ID, USER_ID)).thenReturn(true);
            when(valueOperations.decrement(anyString())).thenThrow(new RuntimeException("Redis down"));

            assertDoesNotThrow(() -> service.unlikeComment(USER_ID, COMMENT_ID));
            verify(statsMapper).decrementLikeCount(COMMENT_ID);
        }
    }

    // ==================== 查询测试 ====================

    @Nested
    @DisplayName("isLiked / getLikeCount 查询")
    class QueryTest {

        @Test
        @DisplayName("Redis 命中时直接返回 true")
        void isLiked_ShouldReturnTrue_WhenRedisHit() {
            when(redisTemplate.hasKey(CommentRedisKeys.userLiked(USER_ID, COMMENT_ID))).thenReturn(true);

            assertTrue(service.isLiked(USER_ID, COMMENT_ID));
            verify(likeRepository, never()).exists(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Redis 未命中时查 DB 并回填")
        void isLiked_ShouldQueryDB_WhenRedisMiss() {
            when(redisTemplate.hasKey(CommentRedisKeys.userLiked(USER_ID, COMMENT_ID))).thenReturn(false);
            when(likeRepository.exists(COMMENT_ID, USER_ID)).thenReturn(true);

            assertTrue(service.isLiked(USER_ID, COMMENT_ID));
            verify(valueOperations).set(CommentRedisKeys.userLiked(USER_ID, COMMENT_ID), "1");
        }

        @Test
        @DisplayName("getLikeCount Redis 命中")
        void getLikeCount_ShouldReturnFromRedis() {
            when(valueOperations.get(CommentRedisKeys.likeCount(COMMENT_ID))).thenReturn(42);

            assertEquals(42, service.getLikeCount(COMMENT_ID));
            verify(likeRepository, never()).countByCommentId(anyLong());
        }

        @Test
        @DisplayName("getLikeCount Redis 未命中时查 DB 并回填")
        void getLikeCount_ShouldQueryDB_WhenRedisMiss() {
            when(valueOperations.get(CommentRedisKeys.likeCount(COMMENT_ID))).thenReturn(null);
            when(likeRepository.countByCommentId(COMMENT_ID)).thenReturn(10);

            assertEquals(10, service.getLikeCount(COMMENT_ID));
            verify(valueOperations).set(CommentRedisKeys.likeCount(COMMENT_ID), 10);
        }
    }
}
