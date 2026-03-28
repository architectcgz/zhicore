package com.zhicore.content.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostReaderPresenceLeaveRequest {

    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;
}
