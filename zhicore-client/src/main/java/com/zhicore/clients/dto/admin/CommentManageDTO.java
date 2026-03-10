package com.zhicore.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理侧评论视图 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentManageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String userName;
    private String content;
    private int likeCount;
    private LocalDateTime createdAt;
}
