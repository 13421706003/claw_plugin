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
    
    /**
     * 创建充值订单（支持客户端IP）
     * 
     * H5支付等场景需要客户端IP，使用此方法。
     * 
     * @param userId 用户ID
     * @param amountUsd 充值金额（美元）
     * @param channel 支付渠道
     * @param payType 支付方式
     * @param clientIp 客户端IP地址
     * @return 订单信息
     */
    Map<String, Object> createOrder(Long userId, BigDecimal amountUsd, PaymentChannel channel, PayType payType, String clientIp);

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
