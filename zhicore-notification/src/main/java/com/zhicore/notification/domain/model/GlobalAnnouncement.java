package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

/**
 * 全局公告实体
 *
 * @author ZhiCore Team
 */
@Getter
public class GlobalAnnouncement {

    /**
     * 公告ID
     */
    private final String id;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容
     */
    private String content;

    /**
     * 公告类型
     */
    private AnnouncementType type;

    /**
     * 是否激活
     */
    private boolean isActive;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 私有构造函数
     */
    private GlobalAnnouncement(String id, String title, String content, AnnouncementType type) {
        Assert.hasText(id, "公告ID不能为空");
        Assert.hasText(title, "公告标题不能为空");
        Assert.hasText(content, "公告内容不能为空");
        Assert.notNull(type, "公告类型不能为空");

        this.id = id;
        this.title = title;
        this.content = content;
        this.type = type;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 私有构造函数（用于从持久化恢复）
     */
    private GlobalAnnouncement(String id, String title, String content, AnnouncementType type,
                               boolean isActive, LocalDateTime startTime, LocalDateTime endTime,
                               LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.type = type;
        this.isActive = isActive;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = createdAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建公告
     */
    public static GlobalAnnouncement create(String id, String title, String content, AnnouncementType type) {
        return new GlobalAnnouncement(id, title, content, type);
    }

    /**
     * 创建定时公告
     */
    public static GlobalAnnouncement createScheduled(String id, String title, String content,
                                                      AnnouncementType type,
                                                      LocalDateTime startTime, LocalDateTime endTime) {
        GlobalAnnouncement announcement = new GlobalAnnouncement(id, title, content, type);
        announcement.startTime = startTime;
        announcement.endTime = endTime;
        return announcement;
    }

    /**
     * 从持久化恢复
     */
    public static GlobalAnnouncement reconstitute(String id, String title, String content,
                                                   AnnouncementType type, boolean isActive,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   LocalDateTime createdAt) {
        return new GlobalAnnouncement(id, title, content, type, isActive, startTime, endTime, createdAt);
    }

    // ==================== 领域行为 ====================

    /**
     * 激活公告
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 停用公告
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 检查公告是否在有效期内
     */
    public boolean isInEffectivePeriod() {
        if (!this.isActive) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (this.startTime != null && now.isBefore(this.startTime)) {
            return false;
        }
        if (this.endTime != null && now.isAfter(this.endTime)) {
            return false;
        }
        return true;
    }

    /**
     * 更新公告内容
     */
    public void updateContent(String title, String content) {
        Assert.hasText(title, "公告标题不能为空");
        Assert.hasText(content, "公告内容不能为空");
        this.title = title;
        this.content = content;
    }

    /**
     * 公告类型枚举
     */
    @Getter
    public enum AnnouncementType {
        NORMAL(0, "普通"),
        IMPORTANT(1, "重要"),
        URGENT(2, "紧急");

        private final int code;
        private final String description;

        AnnouncementType(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public static AnnouncementType fromCode(int code) {
            for (AnnouncementType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown announcement type code: " + code);
        }
    }
}
