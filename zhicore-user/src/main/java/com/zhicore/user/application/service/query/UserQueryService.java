package com.zhicore.user.application.service.query;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.application.query.view.UserSimpleView;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 用户查询服务。
 *
 * 承接用户读路径的数据查询与视图组装，不包含缓存逻辑。
 * 缓存由 CacheAsideUserQuery 装饰器负责。
 */
@Service("userQueryService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService implements UserQueryPort {

    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;

    /**
     * 根据 ID 获取用户详情。
     *
     * @param userId 用户 ID
     * @return 用户视图对象
     */
    @Override
    public UserVO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        UserVO userVO = UserAssembler.toVO(user);
        userFollowRepository.findStatsByUserId(userId)
                .ifPresent(stats -> {
                    userVO.setFollowersCount(stats.getFollowersCount());
                    userVO.setFollowingCount(stats.getFollowingCount());
                });
        return userVO;
    }

    /**
     * 根据 ID 获取用户简要信息。
     *
     * @param userId 用户 ID
     * @return 用户简要信息
     */
    @Override
    public UserSimpleView getUserSimpleById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return UserAssembler.toSimpleView(user);
    }

    /**
     * 批量获取用户简要信息。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID 到简要信息的映射
     */
    @Override
    public Map<Long, UserSimpleView> batchGetUsersSimple(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserSimpleView> users = new HashMap<>();
        for (User user : userRepository.findByIds(userIds)) {
            users.put(user.getId(), UserAssembler.toSimpleView(user));
        }
        return users;
    }

    /**
     * 获取用户是否允许陌生人消息。
     *
     * @param userId 用户 ID
     * @return 是否允许陌生人消息
     */
    @Override
    public boolean isStrangerMessageAllowed(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return user.isStrangerMessageAllowed();
    }

    /**
     * 根据邮箱获取用户详情。
     *
     * @param email 邮箱
     * @return 用户视图对象
     */
    public UserVO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return UserAssembler.toVO(user);
    }
}
