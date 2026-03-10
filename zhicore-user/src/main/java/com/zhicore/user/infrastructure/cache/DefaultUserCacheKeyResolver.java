package com.zhicore.user.infrastructure.cache;

import com.zhicore.user.application.port.UserCacheKeyResolver;
import org.springframework.stereotype.Component;

/**
 * 用户缓存/锁 key 默认解析实现。
 */
@Component
public class DefaultUserCacheKeyResolver implements UserCacheKeyResolver {

    @Override
    public String userDetail(Long userId) {
        return UserRedisKeys.userDetail(userId);
    }

    @Override
    public String userSimple(Long userId) {
        return UserRedisKeys.userSimple(userId);
    }

    @Override
    public String strangerMessageSetting(Long userId) {
        return UserRedisKeys.strangerMessageSetting(userId);
    }

    @Override
    public String lockDetail(Long userId) {
        return UserRedisKeys.lockDetail(userId);
    }

    @Override
    public String[] allCacheKeys(Long userId) {
        return UserRedisKeys.allCacheKeys(userId);
    }

    @Override
    public String followLock(Long followerId, Long followingId) {
        return UserRedisKeys.followLock(followerId, followingId);
    }

    @Override
    public String blockLock(Long userIdA, Long userIdB) {
        return UserRedisKeys.blockLock(userIdA, userIdB);
    }

    @Override
    public String userTokenPattern(Long userId) {
        return UserRedisKeys.userTokenPattern(userId);
    }
}
