package com.zhicore.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 管理侧文章视图 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostManageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private Long authorId;
    private String authorName;
    private String status;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;
}
