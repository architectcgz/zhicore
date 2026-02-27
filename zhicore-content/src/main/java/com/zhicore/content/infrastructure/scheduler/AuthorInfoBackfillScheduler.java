package com.zhicore.content.infrastructure.scheduler;

import com.zhicore.api.client.UserServiceClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.content.domain.constant.PostConstants;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.infrastructure.cache.LockKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 作者信息回填定时任务
 * 
 * 定期扫描作者信息为默认值的文章，尝试从 user-service 获取作者信息并更新。
 * 
 * 这是一个兜底方案，防止补偿消息失败导致永久脏数据。
 * 
 * 特性：
 * 1. 使用分布式锁保护（多实例部署时防止重复执行）
 * 2. 批量处理，提高效率
 * 3. 使用看门狗自动续期（外部服务调用时间不确定）
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorInfoBackfillScheduler {

    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;  // 不等待，获取不到直接返回
    
    private final PostRepository postRepository;
    private final UserServiceClient userServiceClient;
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    
    /**
     * 每批处理的文章数量
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 回填未知作者信息
     * 
     * 执行时间：每天凌晨 2 点
     * 使用分布式锁确保多实例部署时只有一个实例执行回填任务
     * 使用看门狗自动续期，防止外部服务调用时锁过期
     * 
     * 执行策略：
     * 1. 查询作者信息为默认值的文章
     * 2. 按 ownerId 去重，批量查询用户信息（每批最多 100 个）
     * 3. 使用 updateAuthorInfo 方法更新文章
     * 4. 记录回填进度和结果（成功数量、失败数量）
     * 
     * 注意：单个失败不影响整体流程，确保所有可回填的数据都能处理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void backfillUnknownAuthors() {
        String lockKey = lockKeys.authorInfoBackfill();
        
        // 尝试获取分布式锁（启用看门狗）
        boolean lockAcquired = lockManager.tryLockWithWatchdog(lockKey, LOCK_WAIT_TIME);
        
        if (!lockAcquired) {
            // 获取锁失败，说明其他实例正在执行，直接返回
            log.debug("无法获取作者信息回填锁，跳过本次执行");
            return;
        }
        
        try {
            log.info("开始回填未知作者信息...");
            
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;
            
            try {
                while (true) {
                    // 查询需要回填的文章
                    List<Post> posts = postRepository.findByOwnerNameAndVersion(
                        PostConstants.UNKNOWN_AUTHOR_NAME, 
                        PostConstants.DEFAULT_PROFILE_VERSION, 
                        BATCH_SIZE
                    );
                    
                    if (posts.isEmpty()) {
                        log.info("没有需要回填的文章，任务结束");
                        break;
                    }
                    
                    log.info("查询到 {} 篇需要回填的文章", posts.size());
                    
                    // 按 ownerId 去重，批量查询用户信息
                    List<Long> ownerIds = posts.stream()
                        .map(post -> post.getOwnerId().getValue())
                        .distinct()
                        .collect(Collectors.toList());
                    
                    log.debug("需要查询的用户ID列表: {}", ownerIds);
                    
                    // 批量获取作者信息
                    Map<Long, UserSimpleDTO> authorMap = new HashMap<>();
                    try {
                        ApiResponse<List<UserSimpleDTO>> response = 
                            userServiceClient.getUsersSimple(ownerIds);
                        
                        if (response != null && response.isSuccess() && response.getData() != null) {
                            authorMap = response.getData().stream()
                                .collect(Collectors.toMap(
                                    UserSimpleDTO::getId,
                                    user -> user
                                ));
                            log.info("成功获取 {} 个用户信息", authorMap.size());
                        } else {
                            log.warn("批量获取用户信息失败: response={}", response);
                        }
                    } catch (Exception e) {
                        log.error("批量获取用户信息异常: ownerIds={}", ownerIds, e);
                    }
                    
                    // 更新每篇文章
                    for (Post post : posts) {
                        try {
                            UserSimpleDTO author = authorMap.get(post.getOwnerId().getValue());
                            
                            if (author != null) {
                                // 使用 updateAuthorInfo 方法，统一走版本过滤逻辑
                                int updatedCount = postRepository.updateAuthorInfo(
                                    post.getOwnerId().getValue(),
                                    author.getNickname(),
                                    author.getAvatarId(),
                                    author.getProfileVersion() != null ? author.getProfileVersion() : 0L
                                );
                                
                                if (updatedCount > 0) {
                                    totalSuccess++;
                                    log.debug("回填作者信息成功: postId={}, userId={}, nickname={}", 
                                        post.getId(), post.getOwnerId(), author.getNickname());
                                } else {
                                    log.debug("文章已被其他流程更新，跳过: postId={}, userId={}", 
                                        post.getId(), post.getOwnerId());
                                }
                            } else {
                                totalFailed++;
                                log.warn("回填作者信息失败（用户不存在）: postId={}, userId={}", 
                                    post.getId(), post.getOwnerId());
                            }
                            
                            totalProcessed++;
                            
                        } catch (Exception e) {
                            totalFailed++;
                            log.error("回填作者信息异常: postId={}, userId={}", 
                                post.getId(), post.getOwnerId(), e);
                            // 继续处理下一篇文章，不中断整体流程
                        }
                    }
                    
                    log.info("回填批次完成: 处理={}, 成功={}, 失败={}", 
                        posts.size(), totalSuccess, totalFailed);
                    
                    // 如果本批次处理的文章数量小于批次大小，说明已经处理完所有需要回填的文章
                    if (posts.size() < BATCH_SIZE) {
                        log.info("本批次文章数量小于批次大小，回填任务完成");
                        break;
                    }
                }
                
                log.info("作者信息回填完成: 总处理={}, 总成功={}, 总失败={}", 
                    totalProcessed, totalSuccess, totalFailed);
                
            } catch (Exception e) {
                log.error("作者信息回填任务执行异常: 已处理={}, 已成功={}, 已失败={}", 
                    totalProcessed, totalSuccess, totalFailed, e);
            }
        } finally {
            // 释放分布式锁
            lockManager.unlock(lockKey);
        }
    }
}
