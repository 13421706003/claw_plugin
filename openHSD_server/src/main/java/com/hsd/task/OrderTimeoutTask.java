package com.hsd.task;

import com.hsd.entity.RechargeOrder;
import com.hsd.mapper.RechargeOrderMapper;
import com.hsd.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时关闭定时任务
 * 
 * 系统订单过期时间为 10 分钟，微信订单过期时间为 15 分钟。
 * 定时任务每分钟扫描一次，主动关闭过期的微信待支付订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final PaymentService wechatPayService;

    /** 系统订单过期时间（分钟） */
    private static final int TIMEOUT_MINUTES = 10;

    /** 单次处理最大数量 */
    private static final int BATCH_SIZE = 100;

    /**
     * 每分钟执行一次
     * 
     * 扫描微信渠道、待支付状态、创建时间超过10分钟的订单，
     * 调用微信关单API并更新本地状态。
     */
    @Scheduled(fixedRate = 60000)
    public void closeExpiredWechatOrders() {
        LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        
        List<RechargeOrder> expiredOrders = rechargeOrderMapper.findExpiredWechatOrders(expireThreshold, BATCH_SIZE);
        
        if (expiredOrders.isEmpty()) {
            return;
        }
        
        log.info("[OrderTimeoutTask] 发现 {} 个过期微信订单", expiredOrders.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (RechargeOrder order : expiredOrders) {
            try {
                // 调用微信关单API
                wechatPayService.closeOrder(order.getOrderNo());
                
                // 更新本地状态
                rechargeOrderMapper.updateStatus(order.getOrderNo(), RechargeOrder.STATUS_CLOSED);
                
                successCount++;
                log.info("[OrderTimeoutTask] 订单关闭成功: orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                failCount++;
                log.error("[OrderTimeoutTask] 订单关闭失败: orderNo={}, error={}", 
                    order.getOrderNo(), e.getMessage());
            }
        }
        
        log.info("[OrderTimeoutTask] 本轮处理完成: 成功={}, 失败={}", successCount, failCount);
    }
}
