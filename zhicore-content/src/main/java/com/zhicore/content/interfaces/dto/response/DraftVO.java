package com.zhicore.content.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 草稿视图对象
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftVO {

    /**
     * 草稿ID
     */
    private String id;

    /**
     * 文章ID
     */
    private String postId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 草稿内容
     */
    private String content;

    /**
     * 内容类型：markdown/html/rich
     */
    private String contentType;

    /**
     * 保存时间
     */
    private LocalDateTime savedAt;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 是否自动保存
     */
    private Boolean isAutoSave;

    /**
     * 字数统计
     */
    private Integer wordCount;
}
