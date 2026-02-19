package com.blog.post.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 话题持久化对象
 *
 * @author Blog Team
 */
@Data
@TableName("topics")
public class TopicPO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String slug;

    private String description;

    private String iconUrl;

    private Integer postCount;

    private Integer followerCount;

    private Boolean isFeatured;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
