package com.zhicore.user.application.service.command;

import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理侧用户写服务。
 *
 * 负责 admin 用户状态变更和 token 失效等命令操作，不承载查询逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserCommandService {

    private final UserRepository userRepository;
    private final CacheStore cacheStore;
    private final UserCacheKeyResolver userCacheKeyResolver;

    /**
     * 禁用用户。
     *
     * @param userId 用户 ID
     */
    @Transactional
    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.disable();
        userRepository.update(user);
        log.info("Admin disabled user: userId={}", userId);
    }

    /**
     * 启用用户。
     *
     * @param userId 用户 ID
     */
    @Transactional
    public void enableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.enable();
        userRepository.update(user);
        log.info("Admin enabled user: userId={}", userId);
    }

    /**
     * 使用户所有 token 失效。
     *
     * @param userId 用户 ID
     */
    @Transactional
    public void invalidateUserTokens(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        cacheStore.deletePattern(userCacheKeyResolver.userTokenPattern(userId));
        log.info("Admin invalidated all tokens for user: userId={}", userId);
    }
}
