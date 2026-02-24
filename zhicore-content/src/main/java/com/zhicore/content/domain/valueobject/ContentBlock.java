package com.zhicore.content.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内容块值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentBlock {
    private String type;
    private String content;
    private Object props;
    private Integer order;
}

