package com.zhicore.content.application.command;

/**
 * 保存草稿命令。
 */
public record SaveDraftCommand(
        String content,
        String contentType,
        Boolean autoSave,
        String deviceId
) {
}
