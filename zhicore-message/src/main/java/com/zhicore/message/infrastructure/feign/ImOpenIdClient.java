package com.zhicore.message.infrastructure.feign;

import com.zhicore.message.infrastructure.feign.dto.ImBatchConvertToOpenIdsRequest;
import com.zhicore.message.infrastructure.feign.dto.ImResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "${message.im-bridge.service-name:im-interface}",
        contextId = "imOpenIdClient",
        url = "${message.im-bridge.url:}"
)
public interface ImOpenIdClient {

    @PostMapping("/internal/api/v1/openid/batch-convert-to-openids")
    ImResponse<List<String>> batchConvertToOpenIds(@RequestBody ImBatchConvertToOpenIdsRequest request);
}
