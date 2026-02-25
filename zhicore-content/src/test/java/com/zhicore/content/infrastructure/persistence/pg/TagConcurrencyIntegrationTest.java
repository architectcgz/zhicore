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
                    } catch (Exception e) {
                        // 其他异常忽略
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // 至少一个成功，其余收到冲突
            assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
            assertThat(successCount.get() + conflictCount.get())
                    .isEqualTo(threadCount);
        }
    }
}