package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.common.exception.OptimisticLockException;
import com.zhicore.common.exception.ResourceNotFoundException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // ==================== 4.2 实体不存在测试 ====================

    @Test
    @DisplayName("load 不存在的文章应抛出 ResourceNotFoundException")
    void loadNonExistentPostShouldThrowNotFound() {
        PostId nonExistent = PostId.of(999_999_999L);

        assertThatThrownBy(() -> postRepository.load(nonExistent))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findById 不存在的文章应返回 empty")
    void findByIdNonExistentShouldReturnEmpty() {
        Optional<Post> result = postRepository.findById(PostId.of(999_999_999L));
        assertThat(result).isEmpty();
    }

    // ==================== 4.3 版本冲突与实体不存在区分 ====================

    @Test
    @DisplayName("版本冲突返回 OptimisticLockException，实体不存在返回 ResourceNotFoundException")
    void shouldDistinguishConflictFromNotFound() {
        long postIdValue = System.currentTimeMillis() + 1;
        PostId postId = PostId.of(postIdValue);

        // 创建文章
        Post post = Post.createDraft(postId, UserId.of(1000L), "区分测试");
        postRepository.save(post);

        TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
        TransactionTemplate tx2 = new TransactionTemplate(transactionManager);

        // 场景1：版本冲突 → OptimisticLockException
        Post[] snapshot = new Post[1];
        tx1.executeWithoutResult(status -> snapshot[0] = postRepository.load(postId));
        Post stale = snapshot[0];

        tx2.executeWithoutResult(status -> {
            Post fresh = postRepository.load(postId);
            fresh.updateMeta("先更新", null, null);
            postRepository.update(fresh);
        });

        assertThatThrownBy(() ->
                tx1.executeWithoutResult(status -> {
                    stale.updateMeta("陈旧更新", null, null);
                    postRepository.update(stale);
                })
        ).isInstanceOf(OptimisticLockException.class);

        // 场景2：实体不存在 → ResourceNotFoundException
        PostId nonExistent = PostId.of(888_888_888L);
        assertThatThrownBy(() -> postRepository.load(nonExistent))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

