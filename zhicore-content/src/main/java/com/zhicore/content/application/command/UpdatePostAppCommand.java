package com.zhicore.content.application.command;

import java.util.List;

/**
 * 更新文章命令。
 */
public record UpdatePostAppCommand(
        String title,
        String content,
        Long topicId,
        String coverImageId,
        List<String> tags
) {
}
