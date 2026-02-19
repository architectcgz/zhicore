package com.blog.comment.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建评论请求
 *
 * @author Blog Team
 */
@Schema(description = "创建评论请求")
@Data
public class CreateCommentRequest {

    /**
     * 文章ID
     */
    @Schema(description = "文章ID", example = "1234567890", required = true)
    @NotNull(message = "文章ID不能为空")
    private Long postId;

    /**
     * 评论内容
     */
    @Schema(description = "评论内容", example = "这是一条评论内容", required = true)
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过2000字")
    private String content;

    /**
     * 根评论ID（回复时必填，顶级评论为null）
     */
    @Schema(description = "根评论ID，回复时必填，顶级评论不传", example = "1234567890")
    private Long rootId;

    /**
     * 被回复的评论ID（回复某条具体评论时填写）
     */
    @Schema(description = "被回复的评论ID，回复某条具体评论时填写", example = "1234567891")
    private Long replyToCommentId;

    /**
     * 评论图片文件ID数组（UUIDv7格式，最多9张）
     */
    @Schema(description = "评论图片文件ID数组，最多9张")
    @Size(max = 9, message = "评论图片不能超过9张")
    private String[] imageIds;

    /**
     * 评论语音文件ID（UUIDv7格式）
     */
    @Schema(description = "评论语音文件ID")
    private String voiceId;

    /**
     * 语音时长（秒）
     */
    @Schema(description = "语音时长（秒）", example = "30")
    private Integer voiceDuration;
}
