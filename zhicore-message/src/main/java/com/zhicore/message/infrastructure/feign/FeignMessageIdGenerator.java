package com.zhicore.message.infrastructure.feign;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.port.id.MessageIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 Feign 的消息 ID 生成器适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignMessageIdGenerator implements MessageIdGenerator {

    private final IdGeneratorFeignClient idGeneratorFeignClient;

    @Override
    public Long nextId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response == null ? "null response" : response.getMessage();
            log.error("生成消息 ID 失败: {}", message);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "ID生成失败");
        }
        return response.getData();
    }
}
