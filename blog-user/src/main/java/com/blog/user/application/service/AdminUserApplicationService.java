package com.blog.user.application.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.PageResult;
import com.blog.common.result.ResultCode;
import com.blog.common.util.QueryParamValidator;
import com.blog.user.domain.model.User;
import com.blog.user.domain.repository.UserRepository;
import com.blog.user.infrastructure.cache.UserRedisKeys;
import com.blog.user.interfaces.dto.response.UserManageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员用户管理应用服务
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserApplicationService {

    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 查询用户列表
     *
     * @param keyword 关键词
     * @param status 状态
     * @param page 页码
     * @param size 每页大小
     * @return 用户列表
     */
    @Transactional(readOnly = true)
    public PageResult<UserManageDTO> queryUsers(String keyword, String status, int page, int size) {
        try {
            // 参数验证和规范化
            keyword = QueryParamValidator.validateKeyword(keyword);
            status = QueryParamValidator.validateStatus(status);
            page = QueryParamValidator.validatePage(page);
            size = QueryParamValidator.validateSize(size);
            
            // 计算偏移量
            int offset = QueryParamValidator.calculateOffset(page, size);
            
            // 执行数据库层查询
            List<User> users = userRepository.findByConditions(keyword, status, offset, size);
            long total = userRepository.countByConditions(keyword, status);
            
            // 转换为 DTO
            List<UserManageDTO> dtoList = users.stream()
                    .map(this::convertToManageDTO)
                    .collect(Collectors.toList());
            
            log.debug("Query users: keyword={}, status={}, page={}, size={}, total={}", 
                      keyword, status, page, size, total);
            
            return PageResult.of(page, size, total, dtoList);
            
        } catch (Exception e) {
            log.error("Failed to query users: keyword={}, status={}, page={}, size={}", 
                      keyword, status, page, size, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "查询用户列表失败");
        }
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

        log.info("Admin disabled user: userId={}", userId);
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

        log.info("Admin enabled user: userId={}", userId);
    }

    /**
     * 使用户所有 Token 失效
     *
     * @param userId 用户ID
     */
    @Transactional
    public void invalidateUserTokens(Long userId) {
        // 检查用户是否存在
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 删除用户的所有 Token 缓存
        String accessTokenKey = UserRedisKeys.accessToken(userId);
        String refreshTokenKey = UserRedisKeys.refreshToken(userId);
        
        stringRedisTemplate.delete(accessTokenKey);
        stringRedisTemplate.delete(refreshTokenKey);
        
        // 删除用户相关的所有 Token 键
        Set<String> tokenKeys = stringRedisTemplate.keys(UserRedisKeys.userTokenPattern(userId));
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            stringRedisTemplate.delete(tokenKeys);
        }

        log.info("Admin invalidated all tokens for user: userId={}", userId);
    }

    /**
     * 转换为管理 DTO
     */
    private UserManageDTO convertToManageDTO(User user) {
        return UserManageDTO.builder()
                .id(user.getId())
                .username(user.getUserName())
                .email(user.getEmail())
                .nickname(user.getNickName())
                .avatar(user.getAvatarId())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toList()))
                .build();
    }
}
