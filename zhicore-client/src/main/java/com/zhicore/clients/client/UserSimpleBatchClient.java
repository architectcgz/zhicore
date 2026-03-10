package com.zhicore.api.client;

/**
 * 用户简要信息单查/批查复合契约。
 */
public interface UserSimpleBatchClient extends UserBatchSimpleClient {

    @org.springframework.web.bind.annotation.GetMapping("/api/v1/users/{userId}/simple")
    com.zhicore.common.result.ApiResponse<com.zhicore.api.dto.user.UserSimpleDTO> getUserSimple(
            @org.springframework.web.bind.annotation.PathVariable("userId") Long userId);
}
