package com.hsd.service;

import com.hsd.service.enums.PayType;
import com.hsd.service.enums.PaymentChannel;

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
}
