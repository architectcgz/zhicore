package com.zhicore.user.interfaces.controller;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.UserQueryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户查询控制器。
 *
 * 负责用户读接口，不承载任何写操作。
 */
@Tag(name = "用户查询", description = "用户信息、设置等查询接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserQueryPort userQueryPort;

    /**
     * 获取用户信息。
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    @Operation(summary = "获取用户详细信息", description = "根据用户ID获取用户的详细信息，包括个人资料、统计数据等")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = UserVO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "用户不存在"
            )
    })
    @GetMapping("/{userId}")
    public ApiResponse<UserVO> getUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        return ApiResponse.success(userQueryPort.getUserById(userId));
    }

    /**
     * 获取用户简要信息。
     *
     * @param userId 用户 ID
     * @return 用户简要信息
     */
    @Operation(summary = "获取用户简要信息", description = "获取用户的基本信息，用于列表展示等场景")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = UserSimpleDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "用户不存在"
            )
    })
    @GetMapping("/{userId}/simple")
    public ApiResponse<UserSimpleDTO> getUserSimple(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        return ApiResponse.success(UserAssembler.toSimpleDTO(userQueryPort.getUserSimpleById(userId)));
    }

    /**
     * 批量获取用户简要信息。
     *
     * @param userIds 用户 ID 集合
     * @return 用户简要信息映射
     */
    @Operation(summary = "批量获取用户简要信息", description = "根据用户ID集合批量获取用户简要信息，返回Map结构")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功"
            )
    })
    @PostMapping("/batch/simple")
    public ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsers(
            @Parameter(description = "用户ID集合", required = true)
            @RequestBody Set<Long> userIds) {
        Map<Long, UserSimpleDTO> users = userQueryPort.batchGetUsersSimple(userIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> UserAssembler.toSimpleDTO(entry.getValue())));
        return ApiResponse.success(users);
    }

    /**
     * 获取用户是否允许陌生人消息。
     *
     * @param userId 用户 ID
     * @return 是否允许陌生人消息
     */
    @Operation(summary = "获取陌生人消息设置", description = "获取指定用户是否允许陌生人发送私信")
    @GetMapping("/{userId}/settings/stranger-message")
    public ApiResponse<Boolean> getStrangerMessageSetting(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        return ApiResponse.success(userQueryPort.isStrangerMessageAllowed(userId));
    }
}
