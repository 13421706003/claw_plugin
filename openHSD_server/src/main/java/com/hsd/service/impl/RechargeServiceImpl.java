package com.hsd.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.hsd.entity.RechargeOrder;
import com.hsd.entity.User;
import com.hsd.mapper.RechargeOrderMapper;
import com.hsd.mapper.UserMapper;
import com.hsd.service.OpenRouterService;
import com.hsd.service.PaymentService;
import com.hsd.service.RechargeService;
import com.hsd.service.enums.PayType;
import com.hsd.service.enums.PaymentChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 充值业务服务实现类
 * 
 * 核心业务逻辑实现，负责协调微信支付和 OpenRouter API，
 * 实现完整的充值流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeServiceImpl implements RechargeService {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final UserMapper userMapper;
    private final Map<String, PaymentService> paymentServices;
    private final OpenRouterService openRouterService;

    /** 美元兑人民币汇率（固定 8.0） */
    private static final BigDecimal EXCHANGE_RATE = new BigDecimal("8.00");
    
    /** 订单号时间格式 */
    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public Map<String, Object> getKeyInfo(Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userMapper.findById(userId);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        String openrouterKey = user.getOpenrouterKey();
        String keyLabel = user.getOpenrouterKeyLabel();

        if (openrouterKey == null || openrouterKey.isEmpty()) {
            result.put("success", true);
            result.put("bound", false);
            result.put("keyLabel", null);
            result.put("keyInfo", null);
            return result;
        }

        try {
            JSONObject keyInfo = openRouterService.getCurrentKeyInfo(openrouterKey);
            result.put("success", true);
            result.put("bound", true);
            result.put("keyLabel", keyLabel);
            result.put("keyInfo", buildKeyInfoMap(keyInfo));
        } catch (Exception e) {
            log.error("[RechargeService] 获取Key信息失败", e);
            result.put("success", true);
            result.put("bound", true);
            result.put("keyLabel", keyLabel);
            result.put("keyInfo", null);
            result.put("error", "获取Key信息失败");
        }

        return result;
    }

    private Map<String, Object> buildKeyInfoMap(JSONObject keyInfo) {
        Map<String, Object> map = new HashMap<>();
        if (keyInfo == null) return map;
        
        map.put("limit", keyInfo.getBigDecimal("limit"));
        map.put("usage", keyInfo.getBigDecimal("usage"));
        map.put("usageDaily", keyInfo.getBigDecimal("usage_daily"));
        map.put("usageWeekly", keyInfo.getBigDecimal("usage_weekly"));
        map.put("usageMonthly", keyInfo.getBigDecimal("usage_monthly"));
        map.put("limitRemaining", keyInfo.getBigDecimal("limit_remaining"));
        return map;
    }

    @Override
    @Transactional
    public Map<String, Object> bindKey(Long userId, String apiKey) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userMapper.findById(userId);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        try {
            JSONObject keyInfo = openRouterService.getCurrentKeyInfo(apiKey);
            if (keyInfo == null) {
                result.put("success", false);
                result.put("message", "API Key 无效");
                return result;
            }

            String label = keyInfo.getString("label");
            if (label == null) {
                result.put("success", false);
                result.put("message", "无法获取Key标签信息");
                return result;
            }
            
            String keyHash = openRouterService.findKeyHashByLabel(label);
            if (keyHash == null) {
                result.put("success", false);
                result.put("message", "未找到对应的Key Hash，请确保使用通过系统创建的子Key");
                return result;
            }
            
            String maskedLabel = maskApiKey(apiKey);
            userMapper.updateOpenRouterKey(userId, apiKey, maskedLabel);
            userMapper.updateKeyHash(userId, keyHash);
            
            log.info("[RechargeService] 绑定Key成功: userId={}, keyLabel={}, keyHash={}", userId, label, keyHash);

            result.put("success", true);
            result.put("message", "绑定成功");
            result.put("keyLabel", maskedLabel);
            result.put("keyHash", keyHash);
        } catch (Exception e) {
            log.error("[RechargeService] 绑定Key失败", e);
            result.put("success", false);
            result.put("message", "绑定失败: " + e.getMessage());
        }

        return result;
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 12) {
            return key;
        }
        return key.substring(0, 10) + "****" + key.substring(key.length() - 4);
    }

    @Override
    @Transactional
    public Map<String, Object> createOrder(Long userId, BigDecimal amountUsd) {
        return createOrder(userId, amountUsd, PaymentChannel.WECHAT, PayType.NATIVE);
    }

    @Override
    @Transactional
    public Map<String, Object> createOrder(Long userId, BigDecimal amountUsd, PaymentChannel channel, PayType payType) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userMapper.findById(userId);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        if (user.getOpenrouterKey() == null || user.getOpenrouterKey().isEmpty()) {
            result.put("success", false);
            result.put("message", "请先绑定 OpenRouter API Key");
            return result;
        }

        if (amountUsd.compareTo(BigDecimal.ONE) < 0) {
            result.put("success", false);
            result.put("message", "最低充值金额为 1 美元");
            return result;
        }

        PaymentService paymentService = paymentServices.get(channel.getCode() + "PayService");
        if (paymentService == null) {
            result.put("success", false);
            result.put("message", "不支持的支付渠道: " + channel.getCode());
            return result;
        }

        BigDecimal amountCny = amountUsd.multiply(EXCHANGE_RATE).setScale(2, RoundingMode.HALF_UP);
        int amountCents = amountCny.multiply(new BigDecimal("100")).intValue();

        String orderNo = "RC" + LocalDateTime.now().format(ORDER_NO_FORMAT) + String.format("%04d", (int)(Math.random() * 10000));

        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAmountCny(amountCny);
        order.setAmountUsd(amountUsd);
        order.setExchangeRate(EXCHANGE_RATE);
        order.setStatus(RechargeOrder.STATUS_PENDING);
        order.setPaymentChannel(channel.getCode());
        order.setPaymentType(payType.name());

        try {
            String codeUrl = paymentService.createOrder(orderNo, amountCents, "OpenRouter充值-" + amountUsd + "USD", payType);
            order.setQrcodeUrl(codeUrl);
            rechargeOrderMapper.insert(order);

            result.put("success", true);
            result.put("orderNo", orderNo);
            result.put("qrcodeUrl", codeUrl);
            result.put("amountCny", amountCny);
            result.put("amountUsd", amountUsd);
            result.put("paymentChannel", channel.getCode());
            result.put("paymentType", payType.name());
            result.put("mockMode", paymentService.isMockMode());
        } catch (Exception e) {
            log.error("[RechargeService] 创建订单失败", e);
            result.put("success", false);
            result.put("message", "创建订单失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> getOrderStatus(String orderNo) {
        Map<String, Object> result = new HashMap<>();
        
        RechargeOrder order = rechargeOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            result.put("success", false);
            result.put("message", "订单不存在");
            return result;
        }

        result.put("success", true);
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());
        result.put("statusText", getStatusText(order.getStatus()));
        result.put("amountCny", order.getAmountCny());
        result.put("amountUsd", order.getAmountUsd());
        result.put("qrcodeUrl", order.getQrcodeUrl());
        result.put("paidAt", order.getPaidAt());

        return result;
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case RechargeOrder.STATUS_PENDING -> "待支付";
            case RechargeOrder.STATUS_PAID -> "已支付";
            case RechargeOrder.STATUS_ALLOCATED -> "已完成";
            case RechargeOrder.STATUS_CLOSED -> "已关闭";
            default -> "未知";
        };
    }

    @Override
    @Transactional
    public boolean handlePaySuccess(String orderNo, String channelOrderId, String paidAtStr) {
        RechargeOrder order = rechargeOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            log.error("[RechargeService] 订单不存在: {}", orderNo);
            return false;
        }

        if (order.getStatus() != RechargeOrder.STATUS_PENDING) {
            log.warn("[RechargeService] 订单状态不正确: orderNo={}, status={}", orderNo, order.getStatus());
            return false;
        }

        rechargeOrderMapper.updatePayStatus(orderNo, RechargeOrder.STATUS_PAID, channelOrderId, paidAtStr);

        User user = userMapper.findById(order.getUserId());
        if (user == null) {
            log.error("[RechargeService] 用户不存在: userId={}", order.getUserId());
            return false;
        }
        
        String keyHash = user.getOpenrouterKeyHash();
        String openrouterKey = user.getOpenrouterKey();
        
        if (keyHash == null || keyHash.isEmpty()) {
            log.error("[RechargeService] 用户未绑定Key Hash: userId={}", order.getUserId());
            return false;
        }

        try {
            log.info("[RechargeService] 开始处理支付成功: orderNo={}, keyHash={}", orderNo, keyHash);
            
            JSONObject currentKeyInfo = openRouterService.getCurrentKeyInfo(openrouterKey);
            
            if (currentKeyInfo == null) {
                log.error("[RechargeService] 获取Key信息失败: orderNo={}", orderNo);
                return false;
            }
            
            log.info("[RechargeService] Key信息返回: {}", currentKeyInfo.toJSONString());
            
            BigDecimal currentLimit = BigDecimal.ZERO;
            if (currentKeyInfo.getBigDecimal("limit") != null) {
                currentLimit = currentKeyInfo.getBigDecimal("limit");
            }
            
            BigDecimal newLimit = currentLimit.add(order.getAmountUsd());
            
            log.info("[RechargeService] 准备更新额度: keyHash={}, currentLimit={}, addAmount={}, newLimit={}", 
                     keyHash, currentLimit, order.getAmountUsd(), newLimit);
            
            boolean success = openRouterService.updateKeyLimit(keyHash, newLimit.doubleValue());
            if (success) {
                rechargeOrderMapper.updateStatus(orderNo, RechargeOrder.STATUS_ALLOCATED);
                log.info("[RechargeService] 充值成功: orderNo={}, newLimit={}", orderNo, newLimit);
                return true;
            } else {
                log.error("[RechargeService] 更新额度失败: orderNo={}", orderNo);
                return false;
            }
        } catch (Exception e) {
            log.error("[RechargeService] 处理支付成功失败", e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getOrderHistory(Long userId, int limit) {
        Map<String, Object> result = new HashMap<>();
        
        List<RechargeOrder> orders = rechargeOrderMapper.findByUserId(userId, limit);
        
        result.put("success", true);
        result.put("orders", orders.stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("orderNo", order.getOrderNo());
            map.put("amountCny", order.getAmountCny());
            map.put("amountUsd", order.getAmountUsd());
            map.put("status", order.getStatus());
            map.put("statusText", getStatusText(order.getStatus()));
            map.put("createdAt", order.getCreatedAt());
            map.put("paidAt", order.getPaidAt());
            return map;
        }).toList());

        return result;
    }

    @Override
    public BigDecimal getExchangeRate() {
        return EXCHANGE_RATE;
    }

    @Override
    public Map<String, Object> mockPaySuccess(String orderNo) {
        Map<String, Object> result = new HashMap<>();
        
        RechargeOrder order = rechargeOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            result.put("success", false);
            result.put("message", "订单不存在");
            return result;
        }
        
        PaymentService paymentService = paymentServices.get(order.getPaymentChannel() + "PayService");
        if (paymentService == null || !paymentService.isMockMode()) {
            result.put("success", false);
            result.put("message", "非模拟模式，无法使用模拟支付");
            return result;
        }
        
        if (order.getStatus() != RechargeOrder.STATUS_PENDING) {
            result.put("success", false);
            result.put("message", "订单状态不正确");
            return result;
        }
        
        String mockChannelOrderId = "MOCK_" + System.currentTimeMillis();
        String mockPaidAt = java.time.LocalDateTime.now().toString();
        
        boolean success = handlePaySuccess(orderNo, mockChannelOrderId, mockPaidAt);
        
        if (success) {
            result.put("success", true);
            result.put("message", "模拟支付成功，额度已分配");
            result.put("orderNo", orderNo);
            result.put("channelOrderId", mockChannelOrderId);
        } else {
            result.put("success", false);
            result.put("message", "支付已记录，但额度分配失败，请联系客服处理");
            result.put("orderNo", orderNo);
            result.put("channelOrderId", mockChannelOrderId);
        }
        
        return result;
    }
}
