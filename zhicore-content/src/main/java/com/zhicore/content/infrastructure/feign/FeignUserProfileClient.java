package com.zhicore.content.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.port.client.UserProfileClient;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 user-service 的作者资料客户端适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignUserProfileClient implements UserProfileClient {

    private final ContentUserServiceClient userServiceClient;

    @Override
    public Optional<OwnerSnapshot> getOwnerSnapshot(UserId userId) {
        if (userId == null) {
            return Optional.empty();
        }

        try {
            ApiResponse<Map<Long, UserSimpleDTO>> response =
                    userServiceClient.batchGetUsersSimple(Collections.singleton(userId.getValue()));
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("获取用户信息失败，返回空快照: userId={}, response={}", userId.getValue(), response);
                return Optional.empty();
            }

            UserSimpleDTO userInfo = response.getData().get(userId.getValue());
            if (userInfo == null) {
                return Optional.empty();
            }

            return Optional.of(new OwnerSnapshot(
                    userId,
                    userInfo.getNickname(),
                    userInfo.getAvatarId(),
                    userInfo.getProfileVersion() != null ? userInfo.getProfileVersion() : 0L
            ));
        } catch (Exception e) {
            log.error("调用 user-service 获取用户信息异常: userId={}", userId.getValue(), e);
            return Optional.empty();
        }
    }
}
