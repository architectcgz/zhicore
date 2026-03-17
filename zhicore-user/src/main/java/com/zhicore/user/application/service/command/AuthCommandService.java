package com.zhicore.user.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.command.LoginCommand;
import com.zhicore.user.application.command.RefreshTokenCommand;
import com.zhicore.user.application.dto.TokenVO;
import com.zhicore.user.application.port.security.TokenProvider;
import com.zhicore.user.application.port.store.RefreshTokenStore;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.UserDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 认证应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final UserRepository userRepository;
    private final UserDomainService userDomainService;
    private final TokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return Token视图对象
     */
    @Transactional(readOnly = true)
    public TokenVO login(LoginCommand request) {
        // 1. 根据邮箱查找用户
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ResultCode.LOGIN_FAILED, "邮箱或密码错误"));

        // 2. 验证用户状态
        userDomainService.validateUserStatus(user);

        // 3. 验证密码
        if (!userDomainService.validatePassword(user, request.password())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED, "邮箱或密码错误");
        }

        // 4. 生成Token
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 5. 将 Refresh Token 加入白名单（Redis 不可用时中止登录，避免发出不可用的 Token）
        try {
            storeRefreshToken(user.getId(), refreshToken);
        } catch (Exception e) {
            log.error("Redis 写入 Refresh Token 失败，登录中止: userId={}", user.getId(), e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "系统繁忙，请稍后重试");
        }

        log.info("User logged in: userId={}, email={}", user.getId(), user.getEmail());

        return new TokenVO(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    /**
     * 刷新Token（Token Rotation：每次刷新签发新 Refresh Token，废弃旧 Token）
     *
     * @param request 刷新Token请求
     * @return 新的Token视图对象
     */
    @Transactional(readOnly = true)
    public TokenVO refreshToken(RefreshTokenCommand request) {
        // 1. 验证 Refresh Token 签名
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh Token无效");
        }

        // 2. 从 Token 中获取用户ID和tokenId
        String userIdStr = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        Long userId = Long.valueOf(userIdStr);
        String tokenId = jwtTokenProvider.getTokenIdFromRefreshToken(request.refreshToken());

        // 3. 校验白名单（Redis 中是否存在该 Refresh Token）
        if (!refreshTokenStore.exists(userId, tokenId)) {
            // 可能是重放攻击，清除该用户所有 Refresh Token
            log.warn("Refresh Token 不在白名单中，疑似重放攻击: userId={}, tokenId={}", userId, tokenId);
            revokeAllRefreshTokens(userId);
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh Token已失效，请重新登录");
        }

        // 4. 查找用户并验证状态
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        userDomainService.validateUserStatus(user);

        // 5. Token Rotation：废弃旧 Token，签发新 Token
        refreshTokenStore.revoke(userId, tokenId);
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        storeRefreshToken(userId, newRefreshToken);

        log.info("Token refreshed (rotation): userId={}", userId);

        return new TokenVO(newAccessToken, newRefreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    /**
     * 用户登出：吊销当前 Refresh Token
     *
     * @param userId 用户ID
     * @param refreshToken 当前 Refresh Token
     */
    public void logout(Long userId, String refreshToken) {
        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            String tokenId = jwtTokenProvider.getTokenIdFromRefreshToken(refreshToken);
            if (tokenId != null) {
                refreshTokenStore.revoke(userId, tokenId);
            }
        }
        log.info("User logged out: userId={}", userId);
    }

    /**
     * 吊销用户所有 Refresh Token（修改密码、禁用用户、强制下线时调用）
     *
     * @param userId 用户ID
     */
    public void revokeAllRefreshTokens(Long userId) {
        try {
            long deleted = refreshTokenStore.revokeAll(userId);
            if (deleted > 0) {
                log.info("Revoked all refresh tokens: userId={}, count={}", userId, deleted);
            }
        } catch (Exception e) {
            log.error("Failed to revoke refresh tokens: userId={}", userId, e);
        }
    }

    /**
     * 将 Refresh Token 存入白名单
     *
     * <p>写入失败时抛出异常，确保 login/refreshToken 不会返回一个实际不可用的 Token。
     * 否则客户端拿到 Token 后刷新时会被判定为重放攻击，导致所有设备被强制下线。</p>
     */
    private void storeRefreshToken(Long userId, String refreshToken) {
        String tokenId = jwtTokenProvider.getTokenIdFromRefreshToken(refreshToken);
        refreshTokenStore.store(userId, tokenId, Duration.ofSeconds(jwtTokenProvider.getRefreshTokenExpiration()));
    }
}
