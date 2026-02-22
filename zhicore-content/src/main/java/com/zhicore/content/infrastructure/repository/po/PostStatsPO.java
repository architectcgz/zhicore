package com.zhicore.content.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文章统计持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("post_stats")
public class PostStatsPO {

    @TableId(type = IdType.INPUT)
    private Long postId;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    private Long viewCount;
}
