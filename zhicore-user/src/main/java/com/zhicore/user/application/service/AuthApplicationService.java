package com.zhicore.user.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.dto.TokenVO;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.UserDomainService;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import com.zhicore.user.infrastructure.security.JwtTokenProvider;
import com.zhicore.user.interfaces.dto.request.LoginRequest;
import com.zhicore.user.interfaces.dto.request.RefreshTokenRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private final RedisTemplate<String, Object> redisTemplate;

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

        // 5. 将 Refresh Token 加入白名单
        storeRefreshToken(user.getId(), refreshToken);

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
    public TokenVO refreshToken(RefreshTokenRequest request) {
        // 1. 验证 Refresh Token 签名
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh Token无效");
        }

        // 2. 从 Token 中获取用户ID和tokenId
        String userIdStr = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        Long userId = Long.valueOf(userIdStr);
        String tokenId = jwtTokenProvider.getTokenIdFromRefreshToken(request.getRefreshToken());

        // 3. 校验白名单（Redis 中是否存在该 Refresh Token）
        String whitelistKey = UserRedisKeys.refreshTokenWhitelist(userId, tokenId);
        Boolean exists = redisTemplate.hasKey(whitelistKey);
        if (!Boolean.TRUE.equals(exists)) {
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
        redisTemplate.delete(whitelistKey);
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        storeRefreshToken(userId, newRefreshToken);

        log.info("Token refreshed (rotation): userId={}", userId);

        return new TokenVO(newAccessToken, newRefreshToken, jwtTokenProvider.getAccessTokenExpiration());
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
                redisTemplate.delete(UserRedisKeys.refreshTokenWhitelist(userId, tokenId));
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
            Set<String> keys = redisTemplate.keys(UserRedisKeys.refreshTokenPattern(userId));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Revoked all refresh tokens: userId={}, count={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to revoke refresh tokens: userId={}", userId, e);
        }
    }

    /**
     * 将 Refresh Token 存入白名单
     */
    private void storeRefreshToken(Long userId, String refreshToken) {
        try {
            String tokenId = jwtTokenProvider.getTokenIdFromRefreshToken(refreshToken);
            String key = UserRedisKeys.refreshTokenWhitelist(userId, tokenId);
            long ttl = jwtTokenProvider.getRefreshTokenExpiration();
            redisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to store refresh token in whitelist: userId=", userId, e);
        }
    }
}
