package com.blog.message.infrastructure.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多渠道推送服务
 * 支持 WebSocket（Web端）和 TCP（移动端）推送
 * 
 * 分布式部署说明：
 * - Web 端使用 DistributedWebSocketPushService，通过 Redis Pub/Sub 实现跨节点推送
 * - 移动端 TCP 推送暂未实现
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiChannelPushService implements PushService {

    private final DeviceRegistry deviceRegistry;
    private final DistributedWebSocketPushService distributedWebSocketPushService;
    private final OfflineMessageStore offlineMessageStore;

    @Override
    public void push(String userId, PushMessage message) {
        List<Device> devices = deviceRegistry.getOnlineDevices(userId);

        if (devices.isEmpty()) {
            // 用户离线，存储离线消息
            offlineMessageStore.save(userId, message);
            log.info("User offline, message saved to offline store: userId={}, messageId={}", 
                    userId, message.getMessageId());
            return;
        }

        // 推送到所有在线设备
        for (Device device : devices) {
            try {
                pushToDevice(device, message);
            } catch (PushException e) {
                log.warn("Failed to push to device, will retry: deviceId={}, error={}", 
                        device.getDeviceId(), e.getMessage());
                // 推送失败的设备，存储离线消息
                offlineMessageStore.save(userId, message);
            }
        }
    }

    @Override
    public void pushToDevice(Device device, PushMessage message) {
        switch (device.getType()) {
            case WEB -> {
                // 使用分布式推送服务，支持跨节点消息路由
                distributedWebSocketPushService.push(device.getUserId(), message);
            }
            case IOS, ANDROID -> {
                // TCP 推送暂未实现，存储离线消息
                log.debug("TCP push not implemented, saving to offline store: deviceId={}", 
                        device.getDeviceId());
                offlineMessageStore.save(device.getUserId(), message);
            }
        }
    }

    /**
     * 推送离线消息给用户
     * 当用户上线时调用
     *
     * @param userId 用户ID
     * @param device 设备
     */
    public void pushOfflineMessages(String userId, Device device) {
        List<PushMessage> offlineMessages = offlineMessageStore.getAndClear(userId);

        if (offlineMessages.isEmpty()) {
            return;
        }

        log.info("Pushing offline messages: userId={}, count={}", userId, offlineMessages.size());

        for (PushMessage message : offlineMessages) {
            try {
                pushToDevice(device, message);
            } catch (PushException e) {
                // 推送失败，重新存入离线队列
                offlineMessageStore.save(userId, message);
                log.warn("Failed to deliver offline message: userId={}, messageId={}", 
                        userId, message.getMessageId());
            }
        }
    }
}
