package com.zhicore.message.infrastructure.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地连接注册表
 * 
 * 跟踪当前节点上的 WebSocket 连接，用于分布式消息路由。
 * 当收到 Redis Pub/Sub 广播消息时，通过此注册表判断目标用户是否连接到本节点。
 * 
 * 与 DeviceRegistry 的区别：
 * - DeviceRegistry: 全局设备注册表（Redis），记录所有节点的设备信息
 * - LocalConnectionRegistry: 本地连接注册表（内存），仅记录当前节点的连接
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class LocalConnectionRegistry {

    /**
     * 本地连接的用户ID集合
     * Key: userId
     * Value: sessionId 集合（一个用户可能有多个连接，如多个浏览器标签页）
     */
    private final ConcurrentHashMap<String, Set<String>> localConnections = new ConcurrentHashMap<>();

    /**
     * 注册本地连接
     *
     * @param userId 用户ID
     * @param sessionId WebSocket session ID
     */
    public void register(String userId, String sessionId) {
        localConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        log.debug("Local connection registered: userId={}, sessionId={}, totalSessions={}", 
                userId, sessionId, localConnections.get(userId).size());
    }

    /**
     * 注销本地连接
     *
     * @param userId 用户ID
     * @param sessionId WebSocket session ID
     */
    public void unregister(String userId, String sessionId) {
        Set<String> sessions = localConnections.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                localConnections.remove(userId);
            }
            log.debug("Local connection unregistered: userId={}, sessionId={}", userId, sessionId);
        }
    }

    /**
     * 检查用户是否连接到本节点
     *
     * @param userId 用户ID
     * @return 是否连接到本节点
     */
    public boolean isUserConnectedLocally(String userId) {
        Set<String> sessions = localConnections.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 获取用户在本节点的所有 session ID
     *
     * @param userId 用户ID
     * @return session ID 集合
     */
    public Set<String> getLocalSessions(String userId) {
        return localConnections.getOrDefault(userId, Set.of());
    }

    /**
     * 获取本节点连接的用户数量
     *
     * @return 用户数量
     */
    public int getLocalUserCount() {
        return localConnections.size();
    }

    /**
     * 获取本节点的总连接数
     *
     * @return 连接数
     */
    public int getLocalConnectionCount() {
        return localConnections.values().stream()
                .mapToInt(Set::size)
                .sum();
    }
}
