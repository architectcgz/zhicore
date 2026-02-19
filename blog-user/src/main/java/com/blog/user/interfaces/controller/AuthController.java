package com.blog.user.interfaces.controller;

import com.blog.common.result.ApiResponse;
import com.blog.user.application.dto.TokenVO;
import com.blog.user.application.service.AuthApplicationService;
import com.blog.user.application.service.UserApplicationService;
import com.blog.user.interfaces.dto.request.LoginRequest;
import com.blog.user.interfaces.dto.request.RefreshTokenRequest;
import com.blog.user.interfaces.dto.request.RegisterRequest;
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
 * @author Blog Team
 */
@Tag(name = "认证管理", description = "用户注册、登录、Token刷新等认证相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final UserApplicationService userApplicationService;

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
                    content = @Content(schema = @Schema(implementation = Long.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数验证失败或用户名/邮箱已存在"
            )
    })
    @PostMapping("/register")
    public ApiResponse<Long> register(
            @Parameter(description = "用户注册信息", required = true)
            @Valid @RequestBody RegisterRequest request) {
        Long userId = userApplicationService.register(request);
        return ApiResponse.success(userId);
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
        TokenVO token = authApplicationService.login(request);
        return ApiResponse.success(token);
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
        TokenVO token = authApplicationService.refreshToken(request);
        return ApiResponse.success(token);
    }
}
