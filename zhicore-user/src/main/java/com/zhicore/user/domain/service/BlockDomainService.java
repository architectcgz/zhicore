package com.zhicore.user.domain.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 拉黑领域服务
 *
 * @author ZhiCore Team
 */
@Service
@RequiredArgsConstructor
public class BlockDomainService {

    private final UserRepository userRepository;

    /**
     * 验证拉黑操作
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     */
    public void validateBlock(Long blockerId, Long blockedId) {
        // 不能拉黑自己
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ResultCode.BLOCK_SELF_NOT_ALLOWED);
        }

        // 检查被拉黑者是否存在
        userRepository.findById(blockedId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "被拉黑的用户不存在"));
    }

    /**
     * 验证取消拉黑操作
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     */
    public void validateUnblock(Long blockerId, Long blockedId) {
        // 不能取消拉黑自己
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ResultCode.BLOCK_SELF_NOT_ALLOWED);
        }
    }
}
