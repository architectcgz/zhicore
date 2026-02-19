package com.blog.user.domain.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.user.domain.model.User;
import com.blog.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户领域服务
 * 
 * 处理跨聚合的业务逻辑
 *
 * @author Blog Team
 */
@Service
@RequiredArgsConstructor
public class UserDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 验证用户注册信息
     *
     * @param email 邮箱
     * @param userName 用户名
     */
    public void validateRegistration(String email, String userName) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByUserName(userName)) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }
    }

    /**
     * 验证密码
     *
     * @param user 用户
     * @param rawPassword 原始密码
     * @return 是否匹配
     */
    public boolean validatePassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /**
     * 加密密码
     *
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 验证用户状态
     *
     * @param user 用户
     */
    public void validateUserStatus(User user) {
        if (!user.isActive()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
    }
}
