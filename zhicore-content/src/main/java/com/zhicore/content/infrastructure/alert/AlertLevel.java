package com.zhicore.content.infrastructure.alert;

/**
 * 告警级别枚举
 */
public enum AlertLevel {
    /**
     * 低级别告警
     */
    LOW(1, "低"),
    
    /**
     * 中级别告警
     */
    MEDIUM(2, "中"),
    
    /**
     * 高级别告警
     */
    HIGH(3, "高"),
    
    /**
     * 严重告警
     */
    CRITICAL(4, "严重");

    private final int priority;
    private final String description;

    AlertLevel(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }
}
