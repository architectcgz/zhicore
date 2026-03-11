package com.zhicore.admin.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.admin.application.dto.UserManageVO;
import com.zhicore.admin.application.sentinel.AdminSentinelHandlers;
import com.zhicore.admin.application.sentinel.AdminSentinelResources;
import com.zhicore.api.client.AdminUserClient;
import com.zhicore.api.dto.admin.UserManageDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理查询服务。
 */
@Service
@RequiredArgsConstructor
public class UserManageQueryService {

    private final AdminUserClient userServiceClient;

    @SentinelResource(
            value = AdminSentinelResources.LIST_USERS,
            blockHandlerClass = AdminSentinelHandlers.class,
            blockHandler = "handleListUsersBlocked"
    )
    public PageResult<UserManageVO> listUsers(String keyword, String status, int page, int size) {
        ApiResponse<PageResult<UserManageDTO>> response =
                userServiceClient.queryUsers(keyword, status, page, size);
        if (!response.isSuccess()) {
            throw new BusinessException(response.getMessage());
        }

        PageResult<UserManageDTO> result = response.getData();
        List<UserManageVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(page, size, result.getTotal(), voList);
    }

    private UserManageVO toVO(UserManageDTO dto) {
        return UserManageVO.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .email(dto.getEmail())
                .nickname(dto.getNickname())
                .avatar(dto.getAvatar())
                .status(dto.getStatus())
                .createdAt(dto.getCreatedAt())
                .roles(dto.getRoles())
                .build();
    }
}
