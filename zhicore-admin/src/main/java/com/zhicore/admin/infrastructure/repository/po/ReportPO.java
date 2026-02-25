package com.zhicore.admin.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 举报持久化对象
 */
@Data
@TableName("reports")
public class ReportPO {
    
    @TableId(type = IdType.INPUT)
    private Long id;
    
    private Long reporterId;
    
    private Long reportedUserId;
    
    private String targetType;
    
    private Long targetId;
    
    private String reason;
    
    private String status;
    
    private Long handlerId;
    
    private String handleAction;
    
    private String handleRemark;
    
    private OffsetDateTime handledAt;
    
    private OffsetDateTime createdAt;
}
