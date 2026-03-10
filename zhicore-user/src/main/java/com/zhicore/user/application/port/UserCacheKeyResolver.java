package com.zhicore.user.application.port;

/**
 * 用户服务 application 层缓存/锁 key 解析端口。
 */
public interface UserCacheKeyResolver {

    String userDetail(Long userId);

    String userSimple(Long userId);

    String strangerMessageSetting(Long userId);

    String lockDetail(Long userId);

    String[] allCacheKeys(Long userId);

    String followLock(Long followerId, Long followingId);

    String blockLock(Long userIdA, Long userIdB);

    String userTokenPattern(Long userId);
}
