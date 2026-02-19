package com.blog.post.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存草稿请求
 *
 * @author Blog Team
 */
@Data
public class SaveDraftRequest {

    @NotBlank(message = "草稿内容不能为空")
    private String content;

    /**
     * 内容类型：markdown/html/rich
     */
    private String contentType = "markdown";

    /**
     * 是否自动保存
     */
    private Boolean isAutoSave = false;

    /**
     * 设备ID（可选）
     */
    private String deviceId;
}
