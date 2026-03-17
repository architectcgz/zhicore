package com.zhicore.user.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.sentinel.UserSentinelHandlers;
import com.zhicore.user.application.sentinel.UserSentinelResources;
import com.zhicore.user.domain.model.UserBlock;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 拉黑查询服务。
 */
@Service
@RequiredArgsConstructor
public class BlockQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    @SentinelResource(
            value = UserSentinelResources.GET_BLOCKED_USERS,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetBlockedUsersBlocked"
    )
    public List<UserVO> getBlockedUsers(Long blockerId, int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);

        List<UserBlock> blocks = userBlockRepository.findByBlockerId(blockerId, page, size);
        return mapUsersInOrder(
                blocks.stream().map(UserBlock::getBlockedId).collect(Collectors.toCollection(LinkedHashSet::new)),
                blocks.stream().map(UserBlock::getBlockedId).toList()
        );
    }

    @SentinelResource(
            value = UserSentinelResources.IS_BLOCKED,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleIsBlockedBlocked"
    )
    public boolean isBlocked(Long blockerId, Long blockedId) {
        return userBlockRepository.exists(blockerId, blockedId);
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private List<UserVO> mapUsersInOrder(Set<Long> userIds, List<Long> orderedUserIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        Map<Long, User> usersById = userRepository.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return orderedUserIds.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(UserAssembler::toVO)
                .collect(Collectors.toList());
    }
}
