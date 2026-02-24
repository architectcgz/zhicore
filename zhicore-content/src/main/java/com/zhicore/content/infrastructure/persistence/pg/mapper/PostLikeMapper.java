package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostLikeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章点赞 Mapper（R2）
 */
@Mapper
public interface PostLikeMapper extends BaseMapper<PostLikeEntity> {}

