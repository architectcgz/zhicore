package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Post-Tag 关联实体
 * 
 * 使用复合主键 (postId, tagId)
 *
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("post_tags")
public class PostTagEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID（复合主键之一）
     */
    private Long postId;

    /**
     * 标签ID（复合主键之一）
     */
    private Long tagId;

    /**
     * 关联创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 构造函数（用于创建新关联）
     */
    public PostTagEntity(Long postId, Long tagId) {
        this.postId = postId;
        this.tagId = tagId;
        this.createdAt = OffsetDateTime.now();
    }
}
