package com.hsd.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理前端浏览器 WebSocket 连接的注册表
 * 路径：/ws/web/{userId}
 */
@Slf4j
@Component
public class WebSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> webSessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        WebSocketSession old = webSessions.put(userId, session);
        if (old != null && old.isOpen()) {
            log.warn("[WebSessionRegistry] 用户 {} 存在旧连接，已被新连接覆盖", userId);
        }
        log.info("[WebSessionRegistry] 前端连接已注册：userId={}", userId);
    }

    public void remove(String userId) {
        webSessions.remove(userId);
        log.info("[WebSessionRegistry] 前端连接已移除：userId={}", userId);
    }

    public boolean isOnline(String userId) {
        WebSocketSession session = webSessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 向指定用户的前端推送 JSON 消息
     */
    public boolean pushToUser(String userId, String jsonStr) {
        WebSocketSession session = webSessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.warn("[WebSessionRegistry] 推送失败：用户 {} 前端未连接", userId);
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(jsonStr));
            }
            return true;
        } catch (IOException e) {
            log.error("[WebSessionRegistry] 推送消息失败：userId={}，error={}", userId, e.getMessage());
            return false;
        }
    }
}
