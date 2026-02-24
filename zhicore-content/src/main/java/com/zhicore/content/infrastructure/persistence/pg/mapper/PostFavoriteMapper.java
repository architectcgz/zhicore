package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostFavoriteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章收藏 Mapper（R3）
 */
@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavoriteEntity> {}

