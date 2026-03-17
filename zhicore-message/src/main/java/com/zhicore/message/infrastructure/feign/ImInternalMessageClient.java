package com.zhicore.message.infrastructure.feign;

import com.zhicore.message.infrastructure.feign.dto.ImRecallMessageRequest;
import com.zhicore.message.infrastructure.feign.dto.ImResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "${message.im-bridge.service-name:im-interface}",
        contextId = "imInternalMessageClient",
        url = "${message.im-bridge.url:}"
)
public interface ImInternalMessageClient {

    @PostMapping("/internal/api/v1/message/recall")
    ImResponse<Void> recallMessage(@RequestHeader("X-Internal-Token") String internalToken,
                                   @RequestBody ImRecallMessageRequest request);
}
