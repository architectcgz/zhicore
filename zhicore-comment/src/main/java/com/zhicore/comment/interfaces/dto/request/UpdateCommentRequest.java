package com.zhicore.comment.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新评论请求
 *
 * @author ZhiCore Team
 */
@Schema(description = "更新评论请求")
@Data
public class UpdateCommentRequest {

    /**
     * 评论内容
     */
    @Schema(description = "评论内容", example = "这是更新后的评论内容", required = true)
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过2000字")
    private String content;
}
