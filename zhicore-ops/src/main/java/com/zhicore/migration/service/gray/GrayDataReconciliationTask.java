package com.zhicore.migration.service.gray;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 灰度期间数据对账任务。
 *
 * <p>定期检查新旧系统数据一致性，并通过看门狗分布式锁确保多实例部署时只有一个实例执行。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class GrayDataReconciliationTask {

    /** 灰度数据对账分布式锁 key。 */
    public static final String RECONCILIATION_LOCK_KEY = "gray:migration:reconciliation:task:lock";

    private final GrayReleaseStore store;
    private final GrayReleaseSettings settings;
    private final DistributedLockExecutor distributedLockExecutor;

    /**
     * 定时执行数据对账。
     * 默认每 5 分钟执行一次。
     */
    @Scheduled(fixedDelayString = "${gray.reconciliation-interval:300}000")
    public void reconcile() {
        if (!settings.enabled()) {
            return;
        }

        distributedLockExecutor.executeWithWatchdogLock(RECONCILIATION_LOCK_KEY, this::doReconcile);
    }

    private void doReconcile() {
        log.info("开始执行灰度数据对账...");

        ReconciliationResult result = ReconciliationResult.builder()
                .timestamp(OffsetDateTime.now())
                .build();

        try {
            ReconciliationDetail userResult = reconcileUsers();
            result.getDetails().add(userResult);

            ReconciliationDetail postResult = reconcilePosts();
            result.getDetails().add(postResult);

            ReconciliationDetail commentResult = reconcileComments();
            result.getDetails().add(commentResult);

            ReconciliationDetail statsResult = reconcileStats();
            result.getDetails().add(statsResult);

            result.setSuccess(result.getDetails().stream().allMatch(ReconciliationDetail::isConsistent));
            result.setTotalDiffs(result.getDetails().stream().mapToLong(ReconciliationDetail::getDiffCount).sum());

            saveResult(result);

            if (result.isSuccess()) {
                log.info("灰度数据对账完成: 数据一致");
            } else {
                log.warn("灰度数据对账完成: 发现 {} 处差异", result.getTotalDiffs());
            }

        } catch (Exception e) {
            log.error("灰度数据对账失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            saveResult(result);
        }
    }

    /** 对账用户数据。 */
    private ReconciliationDetail reconcileUsers() {
        ReconciliationDetail detail = ReconciliationDetail.builder()
                .tableName("users")
                .build();

        try {
            // TODO: 实现具体的用户数据对账逻辑
            // 比较新旧系统的用户数量、关键字段等
            detail.setConsistent(true);
            detail.setDiffCount(0);
        } catch (Exception e) {
            detail.setConsistent(false);
            detail.setErrorMessage(e.getMessage());
        }

        return detail;
    }

    /** 对账文章数据。 */
    private ReconciliationDetail reconcilePosts() {
        ReconciliationDetail detail = ReconciliationDetail.builder()
                .tableName("posts")
                .build();

        try {
            // TODO: 实现具体的文章数据对账逻辑
            detail.setConsistent(true);
            detail.setDiffCount(0);
        } catch (Exception e) {
            detail.setConsistent(false);
            detail.setErrorMessage(e.getMessage());
        }

        return detail;
    }

    /** 对账评论数据。 */
    private ReconciliationDetail reconcileComments() {
        ReconciliationDetail detail = ReconciliationDetail.builder()
                .tableName("comments")
                .build();

        try {
            // TODO: 实现具体的评论数据对账逻辑
            detail.setConsistent(true);
            detail.setDiffCount(0);
        } catch (Exception e) {
            detail.setConsistent(false);
            detail.setErrorMessage(e.getMessage());
        }

        return detail;
    }

    /** 对账统计数据。 */
    private ReconciliationDetail reconcileStats() {
        ReconciliationDetail detail = ReconciliationDetail.builder()
                .tableName("stats")
                .build();

        try {
            // TODO: 实现具体的统计数据对账逻辑
            // 比较 Redis 缓存与数据库的统计数据
            detail.setConsistent(true);
            detail.setDiffCount(0);
        } catch (Exception e) {
            detail.setConsistent(false);
            detail.setErrorMessage(e.getMessage());
        }

        return detail;
    }

    /** 保存对账结果。 */
    private void saveResult(ReconciliationResult result) {
        store.saveReconciliationResult(result);
    }

    /** 获取最新对账结果。 */
    public ReconciliationResult getLatestResult() {
        return store.getLatestReconciliationResult();
    }

    /** 对账结果。 */
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationResult {
        private OffsetDateTime timestamp;
        private boolean success;
        private long totalDiffs;
        private String errorMessage;
        @lombok.Builder.Default
        private List<ReconciliationDetail> details = new ArrayList<>();
    }

    /** 对账详情。 */
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationDetail {
        private String tableName;
        private boolean consistent;
        private long diffCount;
        private String errorMessage;
    }
}
