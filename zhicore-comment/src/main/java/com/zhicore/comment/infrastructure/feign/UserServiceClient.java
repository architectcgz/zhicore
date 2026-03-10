package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.client.UserSimpleBatchClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.Map;
import java.util.Set;

/**
 * 用户服务 Feign 客户端
 *
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-user", fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceClient extends UserSimpleBatchClient {

    default ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsers(Set<Long> userIds) {
        return batchGetUsersSimple(userIds);
    }
}
