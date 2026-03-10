package com.zhicore.api.client;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * 用户简要信息批量查询契约。
 */
public interface UserBatchSimpleClient {

    @PostMapping("/api/v1/users/batch/simple")
    ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsersSimple(@RequestBody Set<Long> userIds);
}
