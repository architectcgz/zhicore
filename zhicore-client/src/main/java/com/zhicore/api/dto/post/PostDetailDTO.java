package com.zhicore.api.dto.post;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章详情 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostDetailDTO extends PostDTO {

    private static final long serialVersionUID = 1L;

    /**
     * 原始内容（Markdown 或富文本）
     */
    private String raw;

    /**
     * 渲染后的 HTML 内容
     */
    private String html;
}
