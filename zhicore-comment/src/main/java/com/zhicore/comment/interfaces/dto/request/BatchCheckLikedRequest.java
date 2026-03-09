package com.zhicore.comment.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量检查点赞状态请求
 *
 * @author ZhiCore Team
 */
@Schema(description = "批量检查点赞状态请求")
@Data
public class BatchCheckLikedRequest {

    /**
     * 评论ID列表
     */
    @Schema(description = "评论ID列表，最多100个", required = true)
    @NotEmpty(message = "评论ID列表不能为空")
    @Size(max = 100, message = "评论ID列表不能超过100个")
    private List<@NotNull(message = "评论ID不能为空")
            @Min(value = 1, message = "评论ID必须为正数") Long> commentIds;
}
