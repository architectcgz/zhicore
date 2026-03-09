package com.zhicore.content.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * content 服务专用用户客户端。
 */
@FeignClient(name = "zhicore-user", fallbackFactory = ContentUserServiceFallbackFactory.class)
public interface ContentUserServiceClient {

    @PostMapping("/api/v1/users/batch/simple")
    ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsersSimple(@RequestBody Set<Long> userIds);
}
