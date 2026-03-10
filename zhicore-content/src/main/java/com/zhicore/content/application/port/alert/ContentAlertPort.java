package com.zhicore.content.application.port.alert;

/**
 * 内容服务应用层告警端口。
 */
public interface ContentAlertPort {

    void alertScheduledPublishFailedAfterRetries(Long postId, String lastError, Integer retryCount);

    void alertContentImageCleanupFailed(Long postId, String imageUrl, String errorMessage);
}
