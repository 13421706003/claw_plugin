package com.hsd.service;

import com.hsd.enums.PayType;
import com.hsd.enums.PaymentChannel;

import java.math.BigDecimal;
import java.util.Map;

public interface RechargeService {

    Map<String, Object> getKeyInfo(Long userId);

    Map<String, Object> bindKey(Long userId, String apiKey);

    Map<String, Object> createOrder(Long userId, BigDecimal amountUsd);

    Map<String, Object> createOrder(Long userId, BigDecimal amountUsd, PaymentChannel channel, PayType payType);

    Map<String, Object> getOrderStatus(String orderNo);

    boolean handlePaySuccess(String orderNo, String channelOrderId, String paidAtStr);

    Map<String, Object> getOrderHistory(Long userId, int limit);

    BigDecimal getExchangeRate();

    Map<String, Object> mockPaySuccess(String orderNo);
    
    /**
     * 处理微信订单关闭回调
     * 
     * 当微信发送 TRANSACTION.CLOSED 事件时调用，
     * 更新本地订单状态为已关闭。
     * 
     * @param orderNo 商户订单号
     */
    void handleWechatOrderClosed(String orderNo);
}
