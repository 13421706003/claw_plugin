package com.hsd.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RunIdRegistry {

    private final Map<String, String> registry = new ConcurrentHashMap<>();

    private String buildKey(String userId, String clawId, String messageId) {
        return userId + ":" + clawId + ":" + messageId;
    }

    public void register(String userId, String clawId, String messageId, String runId) {
        String key = buildKey(userId, clawId, messageId);
        registry.put(key, runId);
        log.info("[RunIdRegistry] 注册: key={}, runId={}", key, runId);
    }

    public String getRunId(String userId, String clawId, String messageId) {
        String key = buildKey(userId, clawId, messageId);
        return registry.get(key);
    }

    public void remove(String userId, String clawId, String messageId) {
        String key = buildKey(userId, clawId, messageId);
        registry.remove(key);
        log.info("[RunIdRegistry] 移除: key={}", key);
    }

    public String findRunIdByMessageId(String userId, String messageId) {
        String prefix = userId + ":";
        String suffix = ":" + messageId;
        for (Map.Entry<String, String> entry : registry.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getKey().endsWith(suffix)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void removeByMessageId(String userId, String messageId) {
        String prefix = userId + ":";
        String suffix = ":" + messageId;
        registry.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(prefix) && entry.getKey().endsWith(suffix)
        );
    }
}
