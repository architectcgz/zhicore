package com.zhicore.content.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时发布请求
 *
 * @author ZhiCore Team
 */
@Schema(description = "定时发布请求")
@Data
public class SchedulePublishRequest {

    @Schema(description = "定时发布时间", example = "2024-12-31T10:00:00", required = true)
    @NotNull(message = "定时发布时间不能为空")
    @Future(message = "定时发布时间必须是未来时间")
    private LocalDateTime scheduledAt;
}
