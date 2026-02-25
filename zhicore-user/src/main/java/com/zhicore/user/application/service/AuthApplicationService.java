package com.zhicore.user.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.dto.TokenVO;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.UserDomainService;
import com.zhicore.user.infrastructure.security.JwtTokenProvider;
import com.zhicore.user.interfaces.dto.request.LoginRequest;
import com.zhicore.user.interfaces.dto.request.RefreshTokenRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final UserDomainService userDomainService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return Token视图对象
     */
    @Transactional(readOnly = true)
    public TokenVO login(LoginRequest request) {
        // 1. 根据邮箱查找用户
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ResultCode.LOGIN_FAILED, "邮箱或密码错误"));

        // 2. 验证用户状态
        userDomainService.validateUserStatus(user);

        // 3. 验证密码
        if (!userDomainService.validatePassword(user, request.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED, "邮箱或密码错误");
        }

        // 4. 生成Token
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        log.info("User logged in: userId={}, email={}", user.getId(), user.getEmail());

        return new TokenVO(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    /**
     * 刷新Token
     *
     * @param request 刷新Token请求
     * @return 新的Token视图对象
     */
    @Transactional(readOnly = true)
    public TokenVO refreshToken(RefreshTokenRequest request) {
        // 1. 验证Refresh Token
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh Token无效");
        }

        // 2. 从Token中获取用户ID
        String userIdStr = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        Long userId = Long.valueOf(userIdStr);

        // 3. 查找用户
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 4. 验证用户状态
        userDomainService.validateUserStatus(user);

        // 5. 生成新的Access Token
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        log.info("Token refreshed: userId={}", userId);

        return new TokenVO(accessToken, request.getRefreshToken(), jwtTokenProvider.getAccessTokenExpiration());
    }

    /**
     * 验证Token
     *
     * @param token Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        return jwtTokenProvider.validateToken(token);
    }

    /**
     * 从Token获取用户ID
     *
     * @param token Token
     * @return 用户ID
     */
    public String getUserIdFromToken(String token) {
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
