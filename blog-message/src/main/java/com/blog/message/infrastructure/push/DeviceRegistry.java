package com.blog.message.infrastructure.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备注册表
 * 使用 Redis 存储在线设备信息，支持分布式部署
 *
 * @author Blog Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceRegistry {

    private final RedissonClient redissonClient;

    private static final String DEVICE_KEY_PREFIX = "message:device:";
    private static final String USER_DEVICES_KEY_PREFIX = "message:user:devices:";

    /**
     * 注册设备
     *
     * @param device 设备信息
     */
    public void register(Device device) {
        // 存储设备信息
        String deviceKey = DEVICE_KEY_PREFIX + device.getDeviceId();
        RMap<String, Object> deviceMap = redissonClient.getMap(deviceKey);
        deviceMap.put("deviceId", device.getDeviceId());
        deviceMap.put("userId", device.getUserId());
        deviceMap.put("type", device.getType().name());
        deviceMap.put("connectionId", device.getConnectionId());
        deviceMap.put("connectedAt", device.getConnectedAt().toString());

        // 添加到用户设备列表
        String userDevicesKey = USER_DEVICES_KEY_PREFIX + device.getUserId();
        RMap<String, String> userDevices = redissonClient.getMap(userDevicesKey);
        userDevices.put(device.getDeviceId(), device.getConnectionId());

        log.info("Device registered: userId={}, deviceId={}, type={}", 
                device.getUserId(), device.getDeviceId(), device.getType());
    }

    /**
     * 注销设备
     *
     * @param deviceId 设备ID
     */
    public void unregister(String deviceId) {
        String deviceKey = DEVICE_KEY_PREFIX + deviceId;
        RMap<String, Object> deviceMap = redissonClient.getMap(deviceKey);
        
        if (deviceMap.isExists()) {
            String userId = (String) deviceMap.get("userId");
            
            // 从用户设备列表移除
            if (userId != null) {
                String userDevicesKey = USER_DEVICES_KEY_PREFIX + userId;
                RMap<String, String> userDevices = redissonClient.getMap(userDevicesKey);
                userDevices.remove(deviceId);
            }
            
            // 删除设备信息
            deviceMap.delete();
            
            log.info("Device unregistered: deviceId={}", deviceId);
        }
    }

    /**
     * 获取用户的在线设备列表
     *
     * @param userId 用户ID
     * @return 设备列表
     */
    public List<Device> getOnlineDevices(String userId) {
        String userDevicesKey = USER_DEVICES_KEY_PREFIX + userId;
        RMap<String, String> userDevices = redissonClient.getMap(userDevicesKey);
        
        if (!userDevices.isExists() || userDevices.isEmpty()) {
            return new ArrayList<>();
        }

        return userDevices.keySet().stream()
                .map(this::getDevice)
                .filter(device -> device != null)
                .collect(Collectors.toList());
    }

    /**
     * 获取设备信息
     *
     * @param deviceId 设备ID
     * @return 设备信息
     */
    public Device getDevice(String deviceId) {
        String deviceKey = DEVICE_KEY_PREFIX + deviceId;
        RMap<String, Object> deviceMap = redissonClient.getMap(deviceKey);
        
        if (!deviceMap.isExists()) {
            return null;
        }

        try {
            return Device.builder()
                    .deviceId((String) deviceMap.get("deviceId"))
                    .userId((String) deviceMap.get("userId"))
                    .type(DeviceType.valueOf((String) deviceMap.get("type")))
                    .connectionId((String) deviceMap.get("connectionId"))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse device info: deviceId={}", deviceId, e);
            return null;
        }
    }

    /**
     * 检查用户是否在线
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isOnline(String userId) {
        String userDevicesKey = USER_DEVICES_KEY_PREFIX + userId;
        RMap<String, String> userDevices = redissonClient.getMap(userDevicesKey);
        return userDevices.isExists() && !userDevices.isEmpty();
    }
}
