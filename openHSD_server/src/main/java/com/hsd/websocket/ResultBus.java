package com.hsd.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ResultBus：插件回传结果 ↔ SSE 响应之间的异步桥梁
 *
 * 工作原理：
 *   1. SSE 请求进来时，用 messageId 注册一个 BlockingQueue
 *   2. 插件回传 response_chunk / response 时，往对应 Queue 里放消息
 *   3. SSE 线程从 Queue 里 poll，有消息就推给前端，收到终态消息就关闭流
 */
@Slf4j
@Component
public class ResultBus {

    // 标记流结束的哨兵对象
    public static final ResultMessage END_SIGNAL = new ResultMessage("__END__", null, null, true);

    // messageId -> 消息队列
    private final ConcurrentHashMap<String, LinkedBlockingQueue<ResultMessage>> queues =
            new ConcurrentHashMap<>();

    // ----------------------------------------------------------------
    // SSE 端调用
    // ----------------------------------------------------------------

    /**
     * 注册一个 messageId，返回对应的队列（SSE 线程从此队列消费）
     */
    public LinkedBlockingQueue<ResultMessage> register(String messageId) {
        LinkedBlockingQueue<ResultMessage> queue = new LinkedBlockingQueue<>(500);
        queues.put(messageId, queue);
        log.debug("[ResultBus] 注册 messageId={}", messageId);
        return queue;
    }

    /**
     * SSE 流结束后调用，清理队列
     */
    public void unregister(String messageId) {
        queues.remove(messageId);
        log.debug("[ResultBus] 清理 messageId={}", messageId);
    }

    // ----------------------------------------------------------------
    // 插件回传端调用
    // ----------------------------------------------------------------

    /**
     * 推送一个 chunk（delta）
     */
    public void pushChunk(String messageId, String chunk, Integer seq) {
        offer(messageId, new ResultMessage("chunk", chunk, seq, false));
    }

    /**
     * 推送终态（completed / error），同时推入结束哨兵
     */
    public void pushFinal(String messageId, String status, String result) {
        offer(messageId, new ResultMessage(status, result, null, false));
        offer(messageId, END_SIGNAL);
    }

    // ----------------------------------------------------------------
    // 内部
    // ----------------------------------------------------------------

    private void offer(String messageId, ResultMessage msg) {
        LinkedBlockingQueue<ResultMessage> queue = queues.get(messageId);
        if (queue == null) {
            log.warn("[ResultBus] 无对应队列，messageId={}，消息被丢弃", messageId);
            return;
        }
        if (!queue.offer(msg)) {
            log.warn("[ResultBus] 队列已满，messageId={}，消息被丢弃", messageId);
        }
    }

    // ----------------------------------------------------------------
    // 消息载体
    // ----------------------------------------------------------------

    public record ResultMessage(
            String  type,    // "chunk" | "completed" | "error" | "__END__"
            String  content, // 内容
            Integer seq,     // chunk 序号（仅 chunk 有）
            boolean isEnd    // 是否为结束哨兵
    ) {}
}
