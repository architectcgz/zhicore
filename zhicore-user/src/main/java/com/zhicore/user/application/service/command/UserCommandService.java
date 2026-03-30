package com.zhicore.user.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UploadFileDeleteClient;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.integration.messaging.user.UserProfileUpdatedIntegrationEvent;
import com.zhicore.user.application.command.RegisterCommand;
import com.zhicore.user.application.command.UpdateProfileCommand;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.application.port.event.UserIntegrationEventPort;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.RoleRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.UserDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.UUID;

/**
 * 用户应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserDomainService userDomainService;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final UploadFileDeleteClient uploadFileDeleteClient;
    private final AuthCommandService authCommandService;
    private final CacheStore cacheStore;
    private final UserCacheKeyResolver userCacheKeyResolver;
    private final TransactionCommitSignal transactionCommitSignal;
    private final UserIntegrationEventPort userIntegrationEventPort;

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 用户ID
     */
    @Transactional
    public Long register(RegisterCommand request) {
        // 1. 验证注册信息
        userDomainService.validateRegistration(request.email(), request.userName());

        // 2. 生成用户ID
        ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
        if (!idResponse.isSuccess() || idResponse.getData() == null) {
            log.error("生成用户ID失败: {}", idResponse.getMessage());
            throw new BusinessException("用户ID生成失败");
        }
        Long userId = idResponse.getData();

        // 3. 加密密码
        String passwordHash = userDomainService.encodePassword(request.password());

        // 4. 创建用户
        User user = User.create(userId, request.userName(), request.email(), passwordHash);

        // 5. 分配默认角色
        roleRepository.findByName(Role.ROLE_USER)
                .ifPresent(user::assignRole);

        // 6. 保存用户
        userRepository.save(user);

        log.info("User registered successfully: userId={}, userName={}",
            userId, request.userName());
        return userId;
    }

    /**
     * 更新用户资料
     *
     * @param userId 用户ID
     * @param request 更新请求
     */
    @Transactional
    public void updateProfile(Long userId, UpdateProfileCommand request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 领域行为：更新资料
        user.updateProfile(request.nickName(), request.avatarId(), request.bio());

        // 持久化（使用乐观锁确保版本号单调递增）
        User updatedUser = userRepository.updateWithOptimisticLock(user);

        userIntegrationEventPort.publish(new UserProfileUpdatedIntegrationEvent(
                newEventId(),
                Instant.now(),
                userId,
                updatedUser.getUserName(),
                updatedUser.getNickName(),
                updatedUser.getAvatarId(),
                updatedUser.getBio(),
                updatedUser.getProfileVersion()
        ));

        // 缓存失效延迟到事务提交后，避免事务回滚但缓存已被清除的不一致
        registerCacheEviction(userId);

        log.info("User profile updated and integration event queued: userId={}, version={}",
            userId, updatedUser.getProfileVersion());
    }

    /**
     * 禁用用户（同时吊销所有 Refresh Token + 写入 Outbox 事件）
     *
     * @param userId 用户ID
     */
    @Transactional
    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.disable();
        userRepository.update(user);

        // 缓存失效 + 吊销 Token 均延迟到事务提交后执行
        transactionCommitSignal.afterCommit(() -> {
            evictUserCache(userId);
            authCommandService.revokeAllRefreshTokens(userId);
        });

        log.info("User disabled: userId={}", userId);
    }

    /**
     * 启用用户（写入 Outbox 事件）
     *
     * @param userId 用户ID
     */
    @Transactional
    public void enableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.enable();
        userRepository.update(user);

        // 缓存失效延迟到事务提交后
        registerCacheEviction(userId);

        log.info("User enabled: userId={}", userId);
    }

    /**
     * 分配角色
     *
     * @param userId 用户ID
     * @param roleName 角色名称
     */
    @Transactional
    public void assignRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException("角色不存在: " + roleName));

        user.assignRole(role);
        roleRepository.saveUserRole(userId, role.getId());

        log.info("Role assigned: userId={}, role={}", userId, roleName);
    }

    /**
     * 更新用户头像
     *
     * @param userId 用户ID
     * @param avatarUrl 新头像URL
     */
    @Transactional
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 获取旧头像ID
        String oldAvatarId = user.getAvatarId();

        // 更新头像
        user.updateProfile(null, avatarUrl, null);
        userRepository.update(user);

        // 缓存失效延迟到事务提交后
        registerCacheEviction(userId);

        // 删除旧头像文件（如果存在）延迟到事务提交后，避免事务回滚但文件已删
        if (oldAvatarId != null && !oldAvatarId.isEmpty()) {
            registerAvatarDeletion(userId, oldAvatarId, "旧头像");
        }

        log.info("用户头像更新成功: userId={}, avatarUrl={}", userId, avatarUrl);
    }

    /**
     * 更新陌生人消息设置。
     *
     * @param userId 用户ID
     * @param strangerMessageAllowed 是否允许陌生人消息
     */
    @Transactional
    public void updateStrangerMessageSetting(Long userId, boolean strangerMessageAllowed) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.updateStrangerMessageSetting(strangerMessageAllowed);
        userRepository.update(user);
        registerCacheEviction(userId);

        log.info("Stranger message setting updated: userId={}, allowed={}", userId, strangerMessageAllowed);
    }

    /**
     * 删除用户头像
     *
     * @param userId 用户ID
     */
    @Transactional
    public void deleteAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        String avatarId = user.getAvatarId();
        
        // 清空头像ID
        user.updateProfile(null, "", null);
        userRepository.update(user);

        // 缓存失效延迟到事务提交后
        registerCacheEviction(userId);

        // 删除头像文件（如果存在）延迟到事务提交后执行
        if (avatarId != null && !avatarId.isEmpty()) {
            registerAvatarDeletion(userId, avatarId, "头像");
        }

        log.info("用户头像删除成功: userId={}", userId);
    }

    /**
     * 注册事务提交后的缓存失效回调
     */
    private void registerCacheEviction(Long userId) {
        transactionCommitSignal.afterCommit(() -> evictUserCache(userId));
    }

    private void registerAvatarDeletion(Long userId, String fileId, String fileLabel) {
        transactionCommitSignal.afterCommit(() -> {
            try {
                uploadFileDeleteClient.deleteFile(fileId);
                log.info("{}已删除: userId={}, fileId={}", fileLabel, userId, fileId);
            } catch (Exception e) {
                log.warn("删除{}失败: userId={}, fileId={}, error={}",
                        fileLabel, userId, fileId, e.getMessage());
            }
        });
    }

    /**
     * 失效用户缓存
     */
    private void evictUserCache(Long userId) {
        try {
            cacheStore.delete(userCacheKeyResolver.allCacheKeys(userId));
            log.debug("Evicted user cache: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to evict user cache: userId={}, error={}", userId, e.getMessage());
        }
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
