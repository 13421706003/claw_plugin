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
    
    /**
     * 主动查询并同步订单状态
     * 
     * 1. 先查数据库订单状态
     * 2. 如果未支付，调用支付渠道API查询真实状态
     * 3. 如果支付成功，同步更新数据库并分配额度
     * 
     * @param orderNo 商户订单号
     * @return 包含订单状态的响应
     */
    Map<String, Object> queryAndSyncOrderStatus(String orderNo);
    
    /**
     * 取消订单
     * 
     * 用户主动取消订单，调用支付渠道关闭订单并更新数据库状态。
     * 
     * @param orderNo 商户订单号
     * @return 取消结果
     */
    Map<String, Object> cancelOrder(String orderNo);
}
