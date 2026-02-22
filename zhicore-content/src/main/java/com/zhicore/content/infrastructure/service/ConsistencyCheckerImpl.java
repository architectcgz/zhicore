package com.zhicore.content.infrastructure.service;

import com.zhicore.content.domain.exception.DualStorageException;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.repository.PostRepository;
import com.zhicore.content.domain.service.ConsistencyChecker;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import com.zhicore.content.infrastructure.mongodb.repository.PostContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 一致性检查器实现
 * 检查和修复 PostgreSQL 和 MongoDB 之间的数据一致性
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsistencyCheckerImpl implements ConsistencyChecker {

    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;

    /**
     * 检查单篇文章一致性
     */
    @Override
    public ConsistencyCheckResult checkPost(Long postId) {
        log.debug("Checking consistency for post: {}", postId);

        try {
            // 查询 PostgreSQL
            Optional<Post> postOpt = postRepository.findById(postId);
            
            // 查询 MongoDB
            Optional<PostContent> contentOpt = postContentRepository.findByPostId(String.valueOf(postId));

            // 判断一致性
            if (postOpt.isPresent() && contentOpt.isPresent()) {
                // 两边都有数据，检查内容是否一致
                Post post = postOpt.get();
                PostContent content = contentOpt.get();
                
                // 检查基本一致性：postId 是否匹配
                if (!String.valueOf(postId).equals(content.getPostId())) {
                    String details = String.format("PostId mismatch: PG=%s, Mongo=%s", postId, content.getPostId());
                    log.warn("Content mismatch detected for post {}: {}", postId, details);
                    return new ConsistencyCheckResult(postId, false, InconsistencyType.CONTENT_MISMATCH, details);
                }
                
                // 数据一致
                log.debug("Post {} is consistent", postId);
                return new ConsistencyCheckResult(postId, true, InconsistencyType.NONE, "Data is consistent");
                
            } else if (postOpt.isPresent() && !contentOpt.isPresent()) {
                // PostgreSQL 有数据但 MongoDB 没有
                String details = "Post exists in PostgreSQL but content missing in MongoDB";
                log.warn("Inconsistency detected for post {}: {}", postId, details);
                return new ConsistencyCheckResult(postId, false, InconsistencyType.MISSING_IN_MONGO, details);
                
            } else if (!postOpt.isPresent() && contentOpt.isPresent()) {
                // MongoDB 有数据但 PostgreSQL 没有（孤儿数据）
                String details = "Content exists in MongoDB but post missing in PostgreSQL (orphan data)";
                log.warn("Orphan data detected for post {}: {}", postId, details);
                return new ConsistencyCheckResult(postId, false, InconsistencyType.ORPHAN_IN_MONGO, details);
                
            } else {
                // 两边都没有数据
                String details = "Post not found in both PostgreSQL and MongoDB";
                log.debug("Post {} not found in either database", postId);
                return new ConsistencyCheckResult(postId, true, InconsistencyType.NONE, details);
            }
            
        } catch (Exception e) {
            log.error("Error checking consistency for post: {}", postId, e);
            throw new DualStorageException("Failed to check consistency for post: " + postId, e);
        }
    }

    /**
     * 批量检查一致性
     */
    @Override
    public ConsistencyReport batchCheck(List<Long> postIds) {
        log.info("Starting batch consistency check for {} posts", postIds.size());
        long startTime = System.currentTimeMillis();

        List<ConsistencyCheckResult> allResults = new ArrayList<>();
        List<ConsistencyCheckResult> inconsistentResults = new ArrayList<>();

        for (Long postId : postIds) {
            try {
                ConsistencyCheckResult result = checkPost(postId);
                allResults.add(result);
                
                if (!result.isConsistent()) {
                    inconsistentResults.add(result);
                }
            } catch (Exception e) {
                log.error("Error checking post {}: {}", postId, e.getMessage());
                // 继续检查其他文章
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        int consistentCount = allResults.size() - inconsistentResults.size();

        ConsistencyReport report = new ConsistencyReport(
                allResults.size(),
                consistentCount,
                inconsistentResults.size(),
                inconsistentResults,
                duration
        );

        log.info("Batch consistency check completed: {}", report);
        return report;
    }

    /**
     * 修复不一致数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repair(Long postId, RepairStrategy strategy) {
        log.info("Repairing inconsistency for post {} with strategy {}", postId, strategy);

        try {
            // 先检查一致性
            ConsistencyCheckResult checkResult = checkPost(postId);
            
            if (checkResult.isConsistent()) {
                log.info("Post {} is already consistent, no repair needed", postId);
                return;
            }

            // 根据不一致类型和修复策略执行修复
            switch (checkResult.getInconsistencyType()) {
                case MISSING_IN_MONGO:
                    repairMissingInMongo(postId, strategy);
                    break;
                    
                case ORPHAN_IN_MONGO:
                    repairOrphanInMongo(postId, strategy);
                    break;
                    
                case CONTENT_MISMATCH:
                    repairContentMismatch(postId, strategy);
                    break;
                    
                default:
                    log.warn("Unknown inconsistency type for post {}: {}", postId, checkResult.getInconsistencyType());
            }

            log.info("Successfully repaired inconsistency for post {}", postId);
            
        } catch (Exception e) {
            log.error("Failed to repair inconsistency for post {}: {}", postId, e.getMessage(), e);
            throw new DualStorageException("Failed to repair inconsistency for post: " + postId, e);
        }
    }

    /**
     * 修复 MongoDB 缺失的数据
     */
    private void repairMissingInMongo(Long postId, RepairStrategy strategy) {
        log.info("Repairing missing MongoDB content for post {}", postId);

        switch (strategy) {
            case USE_POSTGRES:
                // 从 PostgreSQL 创建 MongoDB 内容（但 PostgreSQL 中已经没有内容字段了）
                // 这种情况下，我们创建一个空的内容记录
                PostContent emptyContent = new PostContent();
                emptyContent.setPostId(String.valueOf(postId));
                emptyContent.setContentType("markdown");
                emptyContent.setRaw("");
                emptyContent.setHtml("");
                emptyContent.setText("");
                postContentRepository.save(emptyContent);
                log.info("Created empty content in MongoDB for post {}", postId);
                break;
                
            case DELETE_INCONSISTENT:
                // 删除 PostgreSQL 中的记录
                postRepository.delete(postId);
                log.info("Deleted post from PostgreSQL: {}", postId);
                break;
                
            default:
                log.warn("Strategy {} not applicable for MISSING_IN_MONGO", strategy);
        }
    }

    /**
     * 修复 MongoDB 中的孤儿数据
     */
    private void repairOrphanInMongo(Long postId, RepairStrategy strategy) {
        log.info("Repairing orphan MongoDB content for post {}", postId);

        switch (strategy) {
            case USE_MONGO:
                // 从 MongoDB 恢复到 PostgreSQL（但这需要完整的元数据，通常不可行）
                log.warn("Cannot restore post to PostgreSQL from MongoDB alone (missing metadata)");
                // 退化为删除策略
                postContentRepository.deleteByPostId(String.valueOf(postId));
                log.info("Deleted orphan content from MongoDB: {}", postId);
                break;
                
            case USE_POSTGRES:
            case DELETE_INCONSISTENT:
                // 删除 MongoDB 中的孤儿数据
                postContentRepository.deleteByPostId(String.valueOf(postId));
                log.info("Deleted orphan content from MongoDB: {}", postId);
                break;
                
            default:
                log.warn("Strategy {} not applicable for ORPHAN_IN_MONGO", strategy);
        }
    }

    /**
     * 修复内容不匹配
     */
    private void repairContentMismatch(Long postId, RepairStrategy strategy) {
        log.info("Repairing content mismatch for post {}", postId);

        switch (strategy) {
            case USE_POSTGRES:
                // 以 PostgreSQL 为准（但 PostgreSQL 中已经没有内容字段了）
                // 这种情况下，我们保留 MongoDB 的内容
                log.warn("Cannot use PostgreSQL as source (no content field), keeping MongoDB content");
                break;
                
            case USE_MONGO:
                // 以 MongoDB 为准（保持现状）
                log.info("Using MongoDB content as source, no action needed");
                break;
                
            case DELETE_INCONSISTENT:
                // 删除两边的数据
                postRepository.delete(postId);
                postContentRepository.deleteByPostId(String.valueOf(postId));
                log.info("Deleted inconsistent data from both databases: {}", postId);
                break;
                
            default:
                log.warn("Strategy {} not applicable for CONTENT_MISMATCH", strategy);
        }
    }

    /**
     * 定时一致性检查
     * 每天凌晨 2 点执行
     */
    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCheck() {
        log.info("Starting scheduled consistency check");

        try {
            // 直接清理孤儿数据（MongoDB 中存在但 PostgreSQL 中不存在的数据）
            // 这是最重要的一致性检查，因为孤儿数据会浪费存储空间
            cleanOrphanData();

            log.info("Scheduled consistency check completed");

        } catch (Exception e) {
            log.error("Error during scheduled consistency check", e);
        }
    }

    /**
     * 清理孤儿数据
     * 查找 MongoDB 中存在但 PostgreSQL 中不存在的数据并删除
     */
    private void cleanOrphanData() {
        log.info("Starting orphan data cleanup");

        try {
            // 1. 获取 MongoDB 中所有 postId
            List<String> mongoPostIds = postContentRepository.findAll()
                    .stream()
                    .map(PostContent::getPostId)
                    .collect(Collectors.toList());

            log.info("Found {} content records in MongoDB", mongoPostIds.size());

            // 2. 检查每个 postId 是否在 PostgreSQL 中存在
            List<String> orphanIds = new ArrayList<>();
            for (String postId : mongoPostIds) {
                try {
                    Long postIdLong = Long.parseLong(postId);
                    Optional<Post> postOpt = postRepository.findById(postIdLong);
                    if (!postOpt.isPresent()) {
                        orphanIds.add(postId);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid postId format in MongoDB: {}", postId);
                    orphanIds.add(postId);
                }
            }

            // 3. 删除孤儿数据
            if (!orphanIds.isEmpty()) {
                log.warn("Found {} orphan records in MongoDB, cleaning up: {}", orphanIds.size(), orphanIds);
                
                for (String orphanId : orphanIds) {
                    try {
                        postContentRepository.deleteByPostId(orphanId);
                        log.info("Deleted orphan content: {}", orphanId);
                    } catch (Exception e) {
                        log.error("Failed to delete orphan content: {}", orphanId, e);
                    }
                }
                
                log.info("Orphan data cleanup completed: deleted {} records", orphanIds.size());
            } else {
                log.info("No orphan data found");
            }

        } catch (Exception e) {
            log.error("Error during orphan data cleanup", e);
        }
    }
}
