package com.zhicore.message.infrastructure.push;

/**
 * 推送服务接口
 *
 * @author ZhiCore Team
 */
public interface PushService {

    /**
     * 推送消息给用户
     *
     * @param userId 用户ID
     * @param message 推送消息
     */
    void push(String userId, PushMessage message);

    /**
     * 推送消息到指定设备
     *
     * @param device 设备
     * @param message 推送消息
     */
    void pushToDevice(Device device, PushMessage message);
}
