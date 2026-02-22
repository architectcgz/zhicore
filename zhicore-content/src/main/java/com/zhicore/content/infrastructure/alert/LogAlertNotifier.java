package com.zhicore.content.infrastructure.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志告警通知器
 * 将告警信息记录到日志中
 */
@Slf4j
@Component
public class LogAlertNotifier implements AlertNotifier {

    @Override
    public void notify(Alert alert) {
        String logMessage = formatAlertMessage(alert);
        
        switch (alert.getLevel()) {
            case CRITICAL:
            case HIGH:
                log.error(logMessage);
                break;
            case MEDIUM:
                log.warn(logMessage);
                break;
            case LOW:
                log.info(logMessage);
                break;
            default:
                log.debug(logMessage);
        }
    }

    /**
     * 格式化告警消息
     *
     * @param alert 告警信息
     * @return 格式化后的消息
     */
    private String formatAlertMessage(Alert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("【告警通知】\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append(String.format("告警ID: %s\n", alert.getId()));
        sb.append(String.format("告警类型: %s\n", alert.getType().getDescription()));
        sb.append(String.format("告警级别: %s\n", alert.getLevel().getDescription()));
        sb.append(String.format("告警标题: %s\n", alert.getTitle()));
        sb.append(String.format("告警消息: %s\n", alert.getMessage()));
        if (alert.getDetails() != null && !alert.getDetails().isEmpty()) {
            sb.append(String.format("告警详情: %s\n", alert.getDetails()));
        }
        if (alert.getResourceId() != null && !alert.getResourceId().isEmpty()) {
            sb.append(String.format("相关资源: %s\n", alert.getResourceId()));
        }
        sb.append(String.format("告警时间: %s\n", alert.getTimestamp()));
        sb.append("=".repeat(80)).append("\n");
        
        return sb.toString();
    }
}
