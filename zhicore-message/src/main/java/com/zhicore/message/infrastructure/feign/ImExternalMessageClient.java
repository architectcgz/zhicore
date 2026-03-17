package com.zhicore.message.infrastructure.feign;

import com.zhicore.message.infrastructure.feign.dto.ImExternalMessageResp;
import com.zhicore.message.infrastructure.feign.dto.ImResponse;
import com.zhicore.message.infrastructure.feign.dto.ImSendMessageByOpenIdRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "${message.im-bridge.service-name:im-interface}",
        contextId = "imExternalMessageClient",
        url = "${message.im-bridge.url:}"
)
public interface ImExternalMessageClient {

    @PostMapping("/api/v1/open/message/send")
    ImResponse<ImExternalMessageResp> sendMessage(@RequestBody ImSendMessageByOpenIdRequest request);
}
