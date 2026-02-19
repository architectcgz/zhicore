package com.blog.user.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新Token请求
 *
 * @author Blog Team
 */
@Schema(description = "刷新令牌请求")
@Data
public class RefreshTokenRequest {

    /**
     * 刷新令牌
     */
    @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
