package com.hsd.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理 userId -> WebSocketSession 的注册表
 * openHSD 插件连接成功后在此注册，断开后移除
 */
@Slf4j
@Component
public class SessionRegistry {

    // key: userId (String), value: 该用户的 openHSD WS session
    private final ConcurrentHashMap<String, WebSocketSession> clawSessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        WebSocketSession old = clawSessions.put(userId, session);
        if (old != null && old.isOpen()) {
            log.warn("[SessionRegistry] 用户 {} 存在旧连接，已被新连接覆盖，旧 sessionId={}", userId, old.getId());
        }
        log.info("[SessionRegistry] 用户 {} 的 openHSD 连接已注册，sessionId={}", userId, session.getId());
    }

    public void remove(String userId) {
        WebSocketSession removed = clawSessions.remove(userId);
        if (removed != null) {
            log.info("[SessionRegistry] 用户 {} 的 openHSD 连接已移除，sessionId={}", userId, removed.getId());
        }
    }

    public WebSocketSession get(String userId) {
        return clawSessions.get(userId);
    }

    public boolean isOnline(String userId) {
        WebSocketSession session = clawSessions.get(userId);
        return session != null && session.isOpen();
    }

    public Collection<WebSocketSession> allSessions() {
        return clawSessions.values();
    }

    public int onlineCount() {
        return (int) clawSessions.values().stream().filter(WebSocketSession::isOpen).count();
    }
}
