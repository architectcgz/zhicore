package com.zhicore.content.infrastructure.scheduler;

import com.zhicore.api.client.UserServiceClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.domain.constant.PostConstants;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorInfoBackfillScheduler {

    private final PostRepository postRepository;
    private final UserServiceClient userServiceClient;
    
    /**
     * 每批处理的文章数量
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 回填未知作者信息
     * 
     * 执行时间：每天凌晨 2 点
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
                    .map(Post::getOwnerId)
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
                        UserSimpleDTO author = authorMap.get(post.getOwnerId());
                        
                        if (author != null) {
                            // 使用 updateAuthorInfo 方法，统一走版本过滤逻辑
                            int updatedCount = postRepository.updateAuthorInfo(
                                post.getOwnerId(),
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
    }
}
