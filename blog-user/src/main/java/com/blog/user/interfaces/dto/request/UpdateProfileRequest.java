package com.blog.user.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新资料请求
 *
 * @author Blog Team
 */
@Schema(description = "用户资料更新请求")
@Data
public class UpdateProfileRequest {

    /**
     * 昵称
     */
    @Schema(description = "昵称，最多50个字符", example = "张三")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickName;

    /**
     * 头像文件ID
     */
    @Schema(description = "头像文件ID（UUIDv7格式）", example = "01933e5f-8b2a-7890-a123-456789abcdef")
    @Size(max = 36, message = "头像文件ID长度不能超过36个字符")
    private String avatarId;

    /**
     * 个人简介
     */
    @Schema(description = "个人简介，最多500个字符", example = "热爱编程的开发者")
    @Size(max = 500, message = "个人简介长度不能超过500个字符")
    private String bio;
}
