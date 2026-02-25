package com.zhicore.user.domain.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 关注领域服务
 *
 * @author ZhiCore Team
 */
@Service
@RequiredArgsConstructor
public class FollowDomainService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    /**
     * 验证关注操作
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     */
    public void validateFollow(Long followerId, Long followingId) {
        // 不能关注自己
        if (followerId.equals(followingId)) {
            throw new BusinessException(ResultCode.FOLLOW_SELF_NOT_ALLOWED);
        }

        // 检查被关注者是否存在
        userRepository.findById(followingId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "被关注的用户不存在"));

        // 检查是否被对方拉黑
        if (userBlockRepository.exists(followingId, followerId)) {
            throw new BusinessException(ResultCode.USER_BLOCKED, "对方已将你拉黑，无法关注");
        }
    }

    /**
     * 验证取消关注操作
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     */
    public void validateUnfollow(Long followerId, Long followingId) {
        // 不能取消关注自己
        if (followerId.equals(followingId)) {
            throw new BusinessException(ResultCode.FOLLOW_SELF_NOT_ALLOWED);
        }
    }
}
