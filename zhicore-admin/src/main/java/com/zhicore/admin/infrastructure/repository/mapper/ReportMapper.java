package com.zhicore.admin.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.admin.infrastructure.repository.po.ReportPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报 Mapper
 */
@Mapper
public interface ReportMapper extends BaseMapper<ReportPO> {
}
