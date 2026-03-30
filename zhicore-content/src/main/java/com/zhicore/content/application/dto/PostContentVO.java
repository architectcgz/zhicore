package com.zhicore.content.application.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章内容视图对象。
 *
 * 保持接口响应与既有内容查询结构兼容，但避免 application 层直接暴露 Mongo 文档类型。
 */
@Data
public class PostContentVO {

    private String id;
    private String postId;
    private String contentType;
    private String raw;
    private String html;
    private String text;
    private Integer wordCount;
    private Integer readingTime;
    private List<ContentBlockVO> blocks;
    private List<MediaResourceVO> media;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    public static class ContentBlockVO {
        private String type;
        private String content;
        private Object props;
        private Integer order;
    }

    @Data
    public static class MediaResourceVO {
        private String type;
        private String url;
        private String thumbnail;
        private Long size;
        private Object metadata;
    }
}
