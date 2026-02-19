package com.blog.post.application.service;

import com.blog.api.event.user.UserProfileUpdatedEvent;
import com.blog.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章作者信息同步服务
 * 
 * <p>负责消费用户资料变更事件并更新文章的作者信息冗余字段。
 * 通过版本号机制确保数据一致性和幂等性。
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li>监听用户资料变更事件</li>
 *   <li>批量更新该用户的所有文章作者信息</li>
 *   <li>使用版本号防止旧数据覆盖新数据</li>
 *   <li>支持消息重复投递的幂等性</li>
 * </ul>
 * 
 * <h3>版本控制机制</h3>
 * <p>使用 owner_profile_version 字段实现乐观锁：
 * <ul>
 *   <li>只更新版本号小于事件版本的文章</li>
 *   <li>防止乱序消息导致的数据不一致</li>
 *   <li>自动忽略过期的事件</li>
 * </ul>
 * 
 * @author Blog Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostAuthorSyncService {

    private final PostRepository postRepository;
    
    /**
     * 同步作者信息到文章
     * 
     * <p>根据用户资料变更事件更新该用户所有文章的作者信息冗余字段。
     * 使用版本号机制确保数据一致性：
     * 
     * <h4>幂等性保证</h4>
     * <ul>
     *   <li>只更新 owner_profile_version < event.version 的文章</li>
     *   <li>防止旧事件覆盖新数据</li>
     *   <li>支持消息重复投递</li>
     *   <li>自动处理乱序消息</li>
     * </ul>
     * 
     * <h4>更新字段</h4>
     * <ul>
     *   <li>owner_nickname: 作者昵称</li>
     *   <li>owner_avatar_id: 作者头像ID</li>
     *   <li>owner_profile_version: 资料版本号</li>
     * </ul>
     * 
     * <h4>事务处理</h4>
     * <p>使用 @Transactional 确保批量更新的原子性，
     * 如果更新失败会回滚，消息会重新投递。
     * 
     * @param event 用户资料变更事件，包含用户ID、昵称、头像ID和版本号
     */
    @Transactional
    public void syncAuthorInfo(UserProfileUpdatedEvent event) {
        log.info("开始同步作者信息: userId={}, nickname={}, avatarId={}, version={}", 
                event.getUserId(), event.getNickname(), event.getAvatarId(), event.getVersion());
        
        // 批量更新该用户的所有文章作者信息
        int updatedCount = postRepository.updateAuthorInfo(
                event.getUserId(),
                event.getNickname(),
                event.getAvatarId(),
                event.getVersion()
        );
        
        log.info("作者信息同步完成: userId={}, version={}, 更新文章数={}", 
                event.getUserId(), event.getVersion(), updatedCount);
    }
}
