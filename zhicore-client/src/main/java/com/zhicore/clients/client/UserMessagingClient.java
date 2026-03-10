package com.zhicore.api.client;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 消息域所需的用户查询复合契约。
 */
public interface UserMessagingClient extends UserRelationClient {

    @GetMapping("/api/v1/users/{userId}/simple")
    ApiResponse<UserSimpleDTO> getUserSimple(@PathVariable("userId") Long userId);
}
