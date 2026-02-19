package com.blog.post.domain.service;

import java.util.List;

/**
 * 一致性检查器接口
 * 检查和修复 PostgreSQL 和 MongoDB 之间的数据一致性
 *
 * @author Blog Team
 */
public interface ConsistencyChecker {

    /**
     * 检查单篇文章一致性
     *
     * @param postId 文章ID
     * @return 检查结果
     */
    ConsistencyCheckResult checkPost(Long postId);

    /**
     * 批量检查一致性
     *
     * @param postIds 文章ID列表
     * @return 检查报告
     */
    ConsistencyReport batchCheck(List<Long> postIds);

    /**
     * 修复不一致数据
     *
     * @param postId 文章ID
     * @param strategy 修复策略（以PG为准/以Mongo为准）
     */
    void repair(Long postId, RepairStrategy strategy);

    /**
     * 定时一致性检查
     * 扫描所有文章，检查数据一致性
     */
    void scheduledCheck();

    /**
     * 一致性检查结果
     */
    class ConsistencyCheckResult {
        private final Long postId;
        private final boolean consistent;
        private final InconsistencyType inconsistencyType;
        private final String details;

        public ConsistencyCheckResult(Long postId, boolean consistent, InconsistencyType inconsistencyType, String details) {
            this.postId = postId;
            this.consistent = consistent;
            this.inconsistencyType = inconsistencyType;
            this.details = details;
        }

        public Long getPostId() {
            return postId;
        }

        public boolean isConsistent() {
            return consistent;
        }

        public InconsistencyType getInconsistencyType() {
            return inconsistencyType;
        }

        public String getDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "ConsistencyCheckResult{" +
                    "postId='" + postId + '\'' +
                    ", consistent=" + consistent +
                    ", inconsistencyType=" + inconsistencyType +
                    ", details='" + details + '\'' +
                    '}';
        }
    }

    /**
     * 一致性检查报告
     */
    class ConsistencyReport {
        private final int totalChecked;
        private final int consistentCount;
        private final int inconsistentCount;
        private final List<ConsistencyCheckResult> inconsistentResults;
        private final long checkDurationMs;

        public ConsistencyReport(int totalChecked, int consistentCount, int inconsistentCount,
                                 List<ConsistencyCheckResult> inconsistentResults, long checkDurationMs) {
            this.totalChecked = totalChecked;
            this.consistentCount = consistentCount;
            this.inconsistentCount = inconsistentCount;
            this.inconsistentResults = inconsistentResults;
            this.checkDurationMs = checkDurationMs;
        }

        public int getTotalChecked() {
            return totalChecked;
        }

        public int getConsistentCount() {
            return consistentCount;
        }

        public int getInconsistentCount() {
            return inconsistentCount;
        }

        public List<ConsistencyCheckResult> getInconsistentResults() {
            return inconsistentResults;
        }

        public long getCheckDurationMs() {
            return checkDurationMs;
        }

        @Override
        public String toString() {
            return "ConsistencyReport{" +
                    "totalChecked=" + totalChecked +
                    ", consistentCount=" + consistentCount +
                    ", inconsistentCount=" + inconsistentCount +
                    ", checkDurationMs=" + checkDurationMs +
                    '}';
        }
    }

    /**
     * 不一致类型
     */
    enum InconsistencyType {
        /** 一致 */
        NONE,
        /** PostgreSQL 有数据但 MongoDB 没有 */
        MISSING_IN_MONGO,
        /** MongoDB 有数据但 PostgreSQL 没有（孤儿数据） */
        ORPHAN_IN_MONGO,
        /** 两边都有数据但内容不一致 */
        CONTENT_MISMATCH
    }

    /**
     * 修复策略
     */
    enum RepairStrategy {
        /** 以 PostgreSQL 为准 */
        USE_POSTGRES,
        /** 以 MongoDB 为准 */
        USE_MONGO,
        /** 删除不一致的数据 */
        DELETE_INCONSISTENT
    }
}
