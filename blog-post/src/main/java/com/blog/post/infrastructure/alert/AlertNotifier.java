package com.blog.post.infrastructure.alert;

/**
 * 告警通知器接口
 * 可以有多种实现：日志、邮件、短信、钉钉、企业微信等
 */
public interface AlertNotifier {
    
    /**
     * 发送告警通知
     *
     * @param alert 告警信息
     */
    void notify(Alert alert);
}
