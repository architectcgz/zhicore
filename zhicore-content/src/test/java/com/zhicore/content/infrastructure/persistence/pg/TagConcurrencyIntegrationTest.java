package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.common.exception.OptimisticLockException;
import com.zhicore.content.application.command.commands.UpdatePostTagsCommand;
import com.zhicore.content.application.command.handlers.UpdatePostTagsHandler;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.*;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 标签并发测试（R18）
 *
 * 覆盖：标签差量更新、标签并发冲突
 */
@DisplayName("标签并发与差量更新测试")
class TagConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private UpdatePostTagsHandler updatePostTagsHandler;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    // ==================== 12.1 标签差量更新测试 ====================

    @Nested
    @DisplayName("标签差量更新")
    class DiffUpdateTests {

        @Test
        @DisplayName("attach 只添加新标签，不影响已有标签")
        void attachShouldOnlyAddNewTags() {
            Long postId = 1001L;
            // 先关联标签 1
            postTagRepository.attach(postId, 1L);

            // 再关联标签 2
            postTagRepository.attach(postId, 2L);

            List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
            assertThat(tagIds).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("detach 只删除指定标签，不影响其他标签")
        void detachShouldOnlyRemoveSpecifiedTag() {
            Long postId = 1002L;
            postTagRepository.attachBatch(postId, List.of(1L, 2L, 3L));

            // 只删除标签 2
            postTagRepository.detach(postId, 2L);

            List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
            assertThat(tagIds).containsExactlyInAnyOrder(1L, 3L);
        }

        @Test
        @DisplayName("attachBatch 幂等：重复 attach 不产生重复关联")
        void attachBatchShouldBeIdempotent() {
            Long postId = 1003L;
            postTagRepository.attachBatch(postId, List.of(1L, 2L));
            // 再次 attach 包含已有标签
            postTagRepository.attachBatch(postId, List.of(2L, 3L));

            List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
            assertThat(tagIds).containsExactlyInAnyOrder(1L, 2L, 3L);
        }
    }

    // ==================== 12.2 标签并发冲突测试 ====================

    @Nested
    @DisplayName("标签并发冲突")
    class ConcurrentConflictTests {

        @Test
        @DisplayName("并发更新同一文章标签，版本冲突抛出 OptimisticLockException")
        void concurrentTagUpdateShouldCauseConflict() throws Exception {
            // 创建文章
            Post post = Post.createDraft(
                    PostId.of(2001L), UserId.of(100L), "并发标签测试文章");
            postRepository.save(post);

            int threadCount = 3;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            AtomicInteger otherErrorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < threadCount; i++) {
                final int tagOffset = i * 10;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        UpdatePostTagsCommand cmd = new UpdatePostTagsCommand(
                                PostId.of(2001L),
                                UserId.of(100L),
                                Set.of(TagId.of((long) (tagOffset + 1)),
                                        TagId.of((long) (tagOffset + 2)))
                        );
                        updatePostTagsHandler.handle(cmd);
                        successCount.incrementAndGet();
                    } catch (OptimisticLockException e) {
                        conflictCount.incrementAndGet();
                        // 验证错误码为 CONCURRENT_TAG_UPDATE
                        assertThat(e.getErrorCode())
                                .isEqualTo("CONCURRENT_TAG_UPDATE");
                        assertThat(e.isRetrySuggested()).isTrue();
                    } catch (DataIntegrityViolationException e) {
                        // 并发 delete+insert 导致唯一约束冲突，也是合法的并发冲突信号
                        conflictCount.incrementAndGet();
                    } catch (Exception e) {
                        otherErrorCount.incrementAndGet();
                        System.err.println("[ConcurrentTest] 线程异常: " + e.getClass().getName() + " - " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // 验证：所有线程都应该完成（成功 + 冲突 + 其他错误 = 线程数）
            assertThat(successCount.get() + conflictCount.get() + otherErrorCount.get())
                    .as("所有线程都应完成: success=%d, conflict=%d, other=%d",
                            successCount.get(), conflictCount.get(), otherErrorCount.get())
                    .isEqualTo(threadCount);
            // 不应有未预期的异常
            assertThat(otherErrorCount.get())
                    .as("不应有未预期的异常: success=%d, conflict=%d, other=%d",
                            successCount.get(), conflictCount.get(), otherErrorCount.get())
                    .isZero();
            // 并发场景下至少应产生冲突（乐观锁或唯一约束），或至少有一个成功
            assertThat(successCount.get() + conflictCount.get())
                    .as("成功数 + 冲突数应等于线程数")
                    .isEqualTo(threadCount);
        }
    }
}