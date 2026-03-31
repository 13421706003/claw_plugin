package com.hsd.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息路由表：记录 messageId → tabId 的映射关系
 *
 * 解决多标签页并发时的响应路由问题：
 *   发消息时注册 (messageId → tabId)，
 *   收到插件响应时查表，精准推回发起请求的那个标签页。
 */
@Slf4j
@Component
public class MessageTabRouter {

    private final ConcurrentHashMap<String, String> routes = new ConcurrentHashMap<>();

    /**
     * 注册路由：消息发出时调用
     *
     * @param messageId 消息 ID
     * @param tabId     发起该请求的标签页 ID
     */
    public void register(String messageId, String tabId) {
        if (messageId == null || messageId.isBlank() || tabId == null || tabId.isBlank()) return;
        routes.put(messageId, tabId);
        log.debug("[MessageTabRouter] 注册路由：messageId={} → tabId={}", messageId, tabId);
    }

    /**
     * 查询路由：收到插件响应时调用
     *
     * @param messageId 消息 ID
     * @return 对应的 tabId，不存在时返回 null
     */
    public String getTabId(String messageId) {
        return messageId != null ? routes.get(messageId) : null;
    }

    /**
     * 删除路由：收到 final response 后清理，防止 Map 无限增长
     *
     * @param messageId 消息 ID
     */
    public void remove(String messageId) {
        if (messageId == null) return;
        routes.remove(messageId);
        log.debug("[MessageTabRouter] 删除路由：messageId={}", messageId);
    }
}
