package com.zhicore.content.application.command;

import java.util.List;

/**
 * 创建文章命令。
 */
public record CreatePostAppCommand(
        String title,
        String content,
        String contentType,
        Long topicId,
        String coverImageId,
        List<String> tags
) {
}
