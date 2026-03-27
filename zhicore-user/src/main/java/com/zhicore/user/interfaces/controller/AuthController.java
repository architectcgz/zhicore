package com.zhicore.user.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.command.LoginCommand;
import com.zhicore.user.application.command.RefreshTokenCommand;
import com.zhicore.user.application.command.RegisterCommand;
import com.zhicore.user.application.dto.TokenVO;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.application.service.command.AuthCommandService;
import com.zhicore.user.application.service.command.UserCommandService;
import com.zhicore.user.interfaces.dto.request.LoginRequest;
import com.zhicore.user.interfaces.dto.request.RefreshTokenRequest;
import com.zhicore.user.interfaces.dto.request.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "认证管理", description = "用户注册、登录、Token刷新等认证相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthCommandService authCommandService;
    private final UserCommandService userCommandService;
    private final UserQueryPort userQueryPort;

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 用户ID
     */
    @Operation(
            summary = "用户注册",
            description = "创建新用户账号，需要提供用户名、邮箱和密码。注册成功后返回用户ID"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "注册成功",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数验证失败或用户名/邮箱已存在"
            )
    })
    @PostMapping("/register")
    public ApiResponse<String> register(
            @Parameter(description = "用户注册信息", required = true)
            @Valid @RequestBody RegisterRequest request) {
        Long userId = userCommandService.register(new RegisterCommand(
                request.getUserName(),
                request.getEmail(),
                request.getPassword()
        ));
        return ApiResponse.success(String.valueOf(userId));
    }

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return Token信息
     */
    @Operation(
            summary = "用户登录",
            description = "使用用户名/邮箱和密码登录系统，成功后返回访问令牌和刷新令牌"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "登录成功",
                    content = @Content(schema = @Schema(implementation = TokenVO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "用户名或密码错误"
            )
    })
    @PostMapping("/login")
    public ApiResponse<TokenVO> login(
            @Parameter(description = "用户登录信息", required = true)
            @Valid @RequestBody LoginRequest request) {
        TokenVO token = authCommandService.login(new LoginCommand(
                request.getEmail(),
                request.getPassword()
        ));
        return ApiResponse.success(token);
    }

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前用户详情
     */
    @Operation(
            summary = "获取当前登录用户",
            description = "根据认证上下文返回当前登录用户的详细信息"
    )
    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUser() {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(userQueryPort.getUserById(userId));
    }

    /**
     * 刷新Token
     *
     * @param request 刷新Token请求
     * @return 新的Token信息
     */
    @Operation(
            summary = "刷新访问令牌",
            description = "使用刷新令牌获取新的访问令牌，延长登录会话"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "刷新成功",
                    content = @Content(schema = @Schema(implementation = TokenVO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "刷新令牌无效或已过期"
            )
    })
    @PostMapping("/refresh")
    public ApiResponse<TokenVO> refreshToken(
            @Parameter(description = "刷新令牌信息", required = true)
            @Valid @RequestBody RefreshTokenRequest request) {
        TokenVO token = authCommandService.refreshToken(new RefreshTokenCommand(
                request.getRefreshToken()
        ));
        return ApiResponse.success(token);
    }
}
