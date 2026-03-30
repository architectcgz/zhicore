package com.zhicore.message.infrastructure.push;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 设备信息
 *
 * @author ZhiCore Team
 */
@Data
@Builder
public class Device {

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 设备类型
     */
    private DeviceType type;

    /**
     * 连接ID（WebSocket session ID 或 TCP connection ID）
     */
    private String connectionId;

    /**
     * 连接时间
     */
    private OffsetDateTime connectedAt;
}
