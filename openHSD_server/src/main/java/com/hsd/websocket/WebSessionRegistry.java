package com.hsd.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理前端浏览器 WebSocket 连接的注册表
 *
 * 支持同一用户多标签页并发连接：
 *   外层 key = userId
 *   内层 key = tabId（来自 URL 路径 /ws/web/{userId}/{tabId}，
 *              旧格式 /ws/web/{userId} 时用 WebSocket sessionId 作为 tabId）
 */
@Slf4j
@Component
public class WebSessionRegistry {

    /** userId → (tabId → WebSocketSession) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>> webSessions
            = new ConcurrentHashMap<>();

    /**
     * 注册连接
     *
     * @param userId  用户 ID
     * @param tabId   标签页 ID（来自 URL 或 sessionId 兜底）
     * @param session WebSocket session
     */
    public void register(String userId, String tabId, WebSocketSession session) {
        webSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                   .put(tabId, session);
        log.info("[WebSessionRegistry] 前端连接已注册：userId={}，tabId={}，当前标签数={}",
                userId, tabId, webSessions.get(userId).size());
    }

    /**
     * 移除连接
     *
     * @param userId 用户 ID
     * @param tabId  标签页 ID
     */
    public void remove(String userId, String tabId) {
        ConcurrentHashMap<String, WebSocketSession> tabs = webSessions.get(userId);
        if (tabs != null) {
            tabs.remove(tabId);
            if (tabs.isEmpty()) {
                webSessions.remove(userId);
            }
        }
        log.info("[WebSessionRegistry] 前端连接已移除：userId={}，tabId={}", userId, tabId);
    }

    /**
     * 判断指定用户是否有任意标签页在线
     */
    public boolean isOnline(String userId) {
        ConcurrentHashMap<String, WebSocketSession> tabs = webSessions.get(userId);
        if (tabs == null || tabs.isEmpty()) return false;
        return tabs.values().stream().anyMatch(WebSocketSession::isOpen);
    }

    /**
     * 向指定标签页精准推送 JSON 消息
     *
     * @param userId  用户 ID
     * @param tabId   标签页 ID
     * @param jsonStr 消息内容
     * @return 是否推送成功
     */
    public boolean pushToTab(String userId, String tabId, String jsonStr) {
        ConcurrentHashMap<String, WebSocketSession> tabs = webSessions.get(userId);
        if (tabs == null) {
            log.warn("[WebSessionRegistry] 精准推送失败：用户 {} 无任何前端连接", userId);
            return false;
        }
        WebSocketSession session = tabs.get(tabId);
        if (session == null || !session.isOpen()) {
            log.warn("[WebSessionRegistry] 精准推送失败：userId={}，tabId={} 不在线", userId, tabId);
            return false;
        }
        return doSend(session, userId, tabId, jsonStr);
    }

    /**
     * 向指定用户的所有在线标签页广播 JSON 消息
     * （用于 file_push 等不针对特定标签页的推送，以及 tabId 缺失时的兜底）
     *
     * @param userId  用户 ID
     * @param jsonStr 消息内容
     * @return 成功推送的标签页数量
     */
    public int pushToUser(String userId, String jsonStr) {
        ConcurrentHashMap<String, WebSocketSession> tabs = webSessions.get(userId);
        if (tabs == null || tabs.isEmpty()) {
            log.warn("[WebSessionRegistry] 广播推送失败：用户 {} 前端未连接", userId);
            return 0;
        }
        int successCount = 0;
        for (var entry : tabs.entrySet()) {
            if (doSend(entry.getValue(), userId, entry.getKey(), jsonStr)) {
                successCount++;
            }
        }
        return successCount;
    }

    // ----------------------------------------------------------------
    // 内部工具
    // ----------------------------------------------------------------

    private boolean doSend(WebSocketSession session, String userId, String tabId, String jsonStr) {
        if (!session.isOpen()) return false;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(jsonStr));
            }
            return true;
        } catch (IOException e) {
            log.error("[WebSessionRegistry] 推送消息失败：userId={}，tabId={}，error={}",
                    userId, tabId, e.getMessage());
            return false;
        }
    }
}
