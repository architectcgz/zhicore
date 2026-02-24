package com.zhicore.content.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 草稿快照
 *
 * 领域层使用的草稿表示，避免直接暴露基础设施文档模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftSnapshot {
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

