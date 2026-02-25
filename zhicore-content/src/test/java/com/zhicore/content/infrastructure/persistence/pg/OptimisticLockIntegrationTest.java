package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.common.exception.OptimisticLockException;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 乐观锁并发冲突集成测试（R6）
 *
 * 目标：验证“并发更新 -> 409 语义”的根因（update 受影响行数为 0）确实会抛出 OptimisticLockException。
 */
@DisplayName("乐观锁并发冲突集成测试")
class OptimisticLockIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    @Test
    @DisplayName("同一版本的两次更新：后写入者应触发并发冲突异常")
    void shouldThrowOptimisticLockExceptionOnStaleVersionUpdate() {
        long postIdValue = System.currentTimeMillis();
        PostId postId = PostId.of(postIdValue);

        Post post = Post.createDraft(postId, UserId.of(1000L), "初始标题");
        postRepository.save(post);

        // 使用两个独立事务模拟“读-改-写”的并发窗口
        TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
        TransactionTemplate tx2 = new TransactionTemplate(transactionManager);

        Post[] snapshot = new Post[1];
        tx1.executeWithoutResult(status -> snapshot[0] = postRepository.load(postId));
        Post stale = snapshot[0];

        // 事务2先更新并提交
        tx2.executeWithoutResult(status -> {
            Post fresh = postRepository.load(postId);
            fresh.updateMeta("事务2更新", null, null);
            postRepository.update(fresh);
        });

        // 事务1使用旧版本更新应冲突
        OptimisticLockException ex = assertThrows(OptimisticLockException.class, () ->
                tx1.executeWithoutResult(status -> {
                    stale.updateMeta("事务1陈旧更新", null, null);
                    postRepository.update(stale);
                })
        );

        assertEquals("CONCURRENT_UPDATE_CONFLICT", ex.getErrorCode());
        assertTrue(ex.isRetrySuggested());
    }
}

