package com.blog.user.application.service;

import com.alibaba.fastjson.JSON;
import com.blog.api.client.IdGeneratorFeignClient;
import com.blog.api.dto.user.UserSimpleDTO;
import com.blog.api.event.user.UserProfileUpdatedEvent;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ApiResponse;
import com.blog.common.result.ResultCode;
import com.blog.user.application.assembler.UserAssembler;
import com.blog.user.application.dto.UserVO;
import com.blog.user.domain.model.OutboxEvent;
import com.blog.user.domain.model.OutboxEventStatus;
import com.blog.user.domain.model.Role;
import com.blog.user.domain.model.User;
import com.blog.user.domain.repository.OutboxEventRepository;
import com.blog.user.domain.repository.RoleRepository;
import com.blog.user.domain.repository.UserFollowRepository;
import com.blog.user.domain.repository.UserRepository;
import com.blog.user.domain.service.UserDomainService;
import com.blog.user.interfaces.dto.request.RegisterRequest;
import com.blog.user.interfaces.dto.request.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户应用服务
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final RoleRepository roleRepository;
    private final UserDomainService userDomainService;
    private final OutboxEventRepository outboxEventRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final com.blog.user.infrastructure.feign.BlogUploadClient blogUploadClient;

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 用户ID
     */
    @Transactional
    public Long register(RegisterRequest request) {
        // 1. 验证注册信息
        userDomainService.validateRegistration(request.getEmail(), request.getUserName());

        // 2. 生成用户ID
        ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
        if (!idResponse.isSuccess() || idResponse.getData() == null) {
            log.error("生成用户ID失败: {}", idResponse.getMessage());
            throw new BusinessException("用户ID生成失败");
        }
        Long userId = idResponse.getData();

        // 3. 加密密码
        String passwordHash = userDomainService.encodePassword(request.getPassword());

        // 4. 创建用户
        User user = User.create(userId, request.getUserName(), request.getEmail(), passwordHash);

        // 5. 分配默认角色
        roleRepository.findByName(Role.ROLE_USER)
                .ifPresent(user::assignRole);

        // 6. 保存用户
        userRepository.save(user);

        log.info("User registered successfully: userId={}, userName={}", userId, request.getUserName());
        return userId;
    }

    /**
     * 根据ID获取用户
     *
     * @param userId 用户ID
     * @return 用户视图对象
     */
    @Transactional(readOnly = true)
    public UserVO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        UserVO userVO = UserAssembler.toVO(user);

        // 获取关注统计
        userFollowRepository.findStatsByUserId(userId)
                .ifPresent(stats -> {
                    userVO.setFollowersCount(stats.getFollowersCount());
                    userVO.setFollowingCount(stats.getFollowingCount());
                });

        return userVO;
    }

    /**
     * 根据ID获取用户简要信息
     *
     * @param userId 用户ID
     * @return 用户简要信息DTO
     */
    @Transactional(readOnly = true)
    public UserSimpleDTO getUserSimpleById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return UserAssembler.toSimpleDTO(user);
    }

    /**
     * 批量获取用户简要信息
     *
     * @param userIds 用户ID集合
     * @return 用户ID到简要信息的映射
     */
    @Transactional(readOnly = true)
    public Map<Long, UserSimpleDTO> batchGetUsersSimple(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = userRepository.findByIds(userIds);
        Map<Long, UserSimpleDTO> result = new HashMap<>();
        for (User user : users) {
            result.put(user.getId(), UserAssembler.toSimpleDTO(user));
        }
        return result;
    }

    /**
     * 根据邮箱获取用户
     *
     * @param email 邮箱
     * @return 用户视图对象
     */
    @Transactional(readOnly = true)
    public UserVO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return UserAssembler.toVO(user);
    }

    /**
     * 更新用户资料
     *
     * @param userId 用户ID
     * @param request 更新请求
     */
    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 领域行为：更新资料
        user.updateProfile(request.getNickName(), request.getAvatarId(), request.getBio());

        // 持久化（使用乐观锁确保版本号单调递增）
        User updatedUser = userRepository.updateWithOptimisticLock(user);

        // 【关键】使用 Transactional Outbox 模式
        // 在同一事务中写入 outbox_events 表，而不是直接发送 MQ
        OutboxEvent outboxEvent = new OutboxEvent(
            UUID.randomUUID().toString(),
            "user-profile-changed",
            "profile-updated",
            String.valueOf(userId),  // sharding key
            JSON.toJSONString(new UserProfileUpdatedEvent(
                userId, 
                updatedUser.getNickName(), 
                updatedUser.getAvatarId(),
                updatedUser.getProfileVersion(),  // 使用 DB 返回的版本号
                LocalDateTime.now()
            )),
            OutboxEventStatus.PENDING,
            LocalDateTime.now()
        );
        
        outboxEventRepository.save(outboxEvent);

        log.info("User profile updated and outbox event created: userId={}, version={}, eventId={}", 
            userId, updatedUser.getProfileVersion(), outboxEvent.getId());
    }

    /**
     * 禁用用户
     *
     * @param userId 用户ID
     */
    @Transactional
    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.disable();
        userRepository.update(user);

        log.info("User disabled: userId={}", userId);
    }

    /**
     * 启用用户
     *
     * @param userId 用户ID
     */
    @Transactional
    public void enableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.enable();
        userRepository.update(user);

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

        // 删除旧头像文件（如果存在）
        if (oldAvatarId != null && !oldAvatarId.isEmpty()) {
            try {
                blogUploadClient.deleteFile(oldAvatarId);
                log.info("旧头像已删除: userId={}, oldFileId={}", userId, oldAvatarId);
            } catch (Exception e) {
                log.warn("删除旧头像失败: userId={}, oldAvatarId={}, error={}", 
                    userId, oldAvatarId, e.getMessage());
                // 不影响主流程，继续执行
            }
        }

        log.info("用户头像更新成功: userId={}, avatarUrl={}", userId, avatarUrl);
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

        // 删除头像文件（如果存在）
        if (avatarId != null && !avatarId.isEmpty()) {
            try {
                blogUploadClient.deleteFile(avatarId);
                log.info("头像文件已删除: userId={}, fileId={}", userId, avatarId);
            } catch (Exception e) {
                log.warn("删除头像文件失败: userId={}, avatarId={}, error={}", 
                    userId, avatarId, e.getMessage());
                // 不影响主流程，继续执行
            }
        }

        log.info("用户头像删除成功: userId={}", userId);
    }
}
