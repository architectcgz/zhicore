package com.zhicore.content.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 媒体资源值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaResource {
    private String type;
    private String url;
    private String thumbnail;
    private Long size;
    private Object metadata;
}

