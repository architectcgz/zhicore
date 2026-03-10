package com.zhicore.content.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 草稿视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftVO {

    private String id;

    private String postId;

    private String userId;

    private String content;

    private String contentType;

    private LocalDateTime savedAt;

    private String deviceId;

    private Boolean isAutoSave;

    private Integer wordCount;
}
