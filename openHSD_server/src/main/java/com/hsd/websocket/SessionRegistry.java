package com.hsd.websocket;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 管理 userId -> 多个 WS Session 的注册表
 * 同一用户可以同时连接多台机器的 openHSD 插件，每台机器用 clawId 区分
 */
@Slf4j
@Component
public class ClawSessionRegistry {

    /**
     * 单个插件连接的元信息
     */
    @Data
    public static class ClawSession {
        private final String           clawId;          // 插件实例 ID（claw_机器名_pid）
        private final WebSocketSession wsSession;       // WS 连接
        private       String           openClawDeviceId; // OpenClaw 机器指纹（sync 后填入）
        private       long             lastHeartbeat;   // 最后心跳时间
    }

    // key: userId → (key: clawId → ClawSession)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ClawSession>> registry =
            new ConcurrentHashMap<>();

    // ----------------------------------------------------------------
    // 注册 / 注销
    // ----------------------------------------------------------------

    /**
     * 插件连接建立时注册
     */
    public void register(String userId, String clawId, WebSocketSession wsSession) {
        registry.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(clawId, new ClawSession(clawId, wsSession));
        log.info("[SessionRegistry] 注册：userId={}，clawId={}，当前在线机器数={}",
                userId, clawId, getClawCount(userId));
    }

    /**
     * 插件连接断开时注销（按 wsSession 查找，因为断开时不一定知道 clawId）
     */
    public void removeBySession(WebSocketSession wsSession) {
        for (Map.Entry<String, ConcurrentHashMap<String, ClawSession>> userEntry : registry.entrySet()) {
            String userId = userEntry.getKey();
            ConcurrentHashMap<String, ClawSession> sessions = userEntry.getValue();
            sessions.entrySet().removeIf(e -> {
                if (e.getValue().getWsSession().getId().equals(wsSession.getId())) {
                    log.info("[SessionRegistry] 注销：userId={}，clawId={}", userId, e.getKey());
                    return true;
                }
                return false;
            });
            if (sessions.isEmpty()) {
                registry.remove(userId);
            }
        }
    }

    // ----------------------------------------------------------------
    // 查询
    // ----------------------------------------------------------------

    /**
     * 获取指定用户的所有在线插件 Session
     */
    public List<ClawSession> getSessions(String userId) {
        ConcurrentHashMap<String, ClawSession> map = registry.get(userId);
        if (map == null) return Collections.emptyList();
        return map.values().stream()
                .filter(s -> s.getWsSession().isOpen())
                .collect(Collectors.toList());
    }

    /**
     * 获取指定用户指定 clawId 的 Session
     */
    public ClawSession getSession(String userId, String clawId) {
        ConcurrentHashMap<String, ClawSession> map = registry.get(userId);
        if (map == null) return null;
        return map.get(clawId);
    }

    /**
     * 判断用户是否有任何在线插件
     */
    public boolean isOnline(String userId) {
        return !getSessions(userId).isEmpty();
    }

    /**
     * 获取用户在线插件数量
     */
    public int getClawCount(String userId) {
        return getSessions(userId).size();
    }

    /**
     * 全局在线插件总数
     */
    public int totalOnlineCount() {
        return registry.values().stream()
                .mapToInt(m -> (int) m.values().stream().filter(s -> s.getWsSession().isOpen()).count())
                .sum();
    }

    // ----------------------------------------------------------------
    // OpenClaw deviceId
    // ----------------------------------------------------------------

    public void setOpenClawDeviceId(String userId, String clawId, String deviceId) {
        ConcurrentHashMap<String, ClawSession> map = registry.get(userId);
        if (map != null && map.containsKey(clawId)) {
            map.get(clawId).setOpenClawDeviceId(deviceId);
            log.info("[SessionRegistry] OpenClaw deviceId 已记录：userId={}，clawId={}，deviceId={}",
                    userId, clawId, deviceId);
        }
    }

    public String getOpenClawDeviceId(String userId) {
        // 返回第一个在线机器的 deviceId（兼容旧接口）
        return getSessions(userId).stream()
                .map(ClawSession::getOpenClawDeviceId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // ----------------------------------------------------------------
    // 发送消息
    // ----------------------------------------------------------------

    /**
     * 向指定用户的指定 clawId 发送消息
     */
    public boolean sendTo(String userId, String clawId, String jsonStr) {
        ClawSession cs = getSession(userId, clawId);
        if (cs == null || !cs.getWsSession().isOpen()) {
            log.warn("[SessionRegistry] 发送失败：userId={}，clawId={} 不在线", userId, clawId);
            return false;
        }
        return doSend(cs.getWsSession(), jsonStr);
    }

    /**
     * 向指定用户的第一个在线插件发送消息（单机场景兼容）
     */
    public boolean sendToFirst(String userId, String jsonStr) {
        List<ClawSession> sessions = getSessions(userId);
        if (sessions.isEmpty()) {
            log.warn("[SessionRegistry] 发送失败：userId={} 无在线插件", userId);
            return false;
        }
        return doSend(sessions.get(0).getWsSession(), jsonStr);
    }

    /**
     * 向指定用户的所有在线插件广播消息
     */
    public void broadcast(String userId, String jsonStr) {
        getSessions(userId).forEach(cs -> doSend(cs.getWsSession(), jsonStr));
    }

    private boolean doSend(WebSocketSession session, String jsonStr) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(jsonStr));
            }
            return true;
        } catch (IOException e) {
            log.error("[SessionRegistry] 消息发送失败：sessionId={}，error={}", session.getId(), e.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------------
    // 心跳
    // ----------------------------------------------------------------

    public void updateHeartbeat(String userId, String clawId) {
        ConcurrentHashMap<String, ClawSession> map = registry.get(userId);
        if (map != null && map.containsKey(clawId)) {
            map.get(clawId).setLastHeartbeat(System.currentTimeMillis());
        }
    }

    // ----------------------------------------------------------------
    // 获取在线机器信息列表（供 API 返回）
    // ----------------------------------------------------------------

    public List<Map<String, Object>> getOnlineClawInfo(String userId) {
        return getSessions(userId).stream().map(cs -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("clawId",           cs.getClawId());
            info.put("openClawDeviceId", cs.getOpenClawDeviceId());
            info.put("lastHeartbeat",    cs.getLastHeartbeat());
            return info;
        }).collect(Collectors.toList());
    }

    // 兼容旧接口
    public WebSocketSession get(String userId) {
        List<ClawSession> sessions = getSessions(userId);
        return sessions.isEmpty() ? null : sessions.get(0).getWsSession();
    }
}
