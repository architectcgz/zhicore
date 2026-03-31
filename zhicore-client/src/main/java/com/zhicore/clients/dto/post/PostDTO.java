package com.zhicore.api.dto.post;

import com.zhicore.api.dto.user.UserSimpleDTO;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文章 DTO
 */
@Data
public class PostDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long ownerId;
    private String title;
    private String excerpt;
    private String coverImage;
    private String status;
    private String editorType;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // 作者信息
    private UserSimpleDTO author;

    // 分类和标签
    private List<String> categories;
    
    /**
     * 标签列表
     */
    private List<TagDTO> tags;

    // 统计信息
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private Integer viewCount;

    // 当前用户状态
    private Boolean liked;
    private Boolean favorited;
}
