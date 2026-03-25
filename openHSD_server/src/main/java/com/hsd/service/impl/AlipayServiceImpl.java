package com.hsd.service.impl;

import com.hsd.config.AlipayConfig;
import com.hsd.service.AlipayService;
import com.hsd.service.dto.PaymentResult;
import com.hsd.service.enums.PayType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付服务实现类
 * 
 * 当前为占位实现，仅支持模拟模式。
 * 真实的支付宝支付功能待后续开发。
 */
@Slf4j
@Service("alipayService")
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {

    private final AlipayConfig alipayConfig;

    @Override
    public String getChannel() {
        return "alipay";
    }

    /**
     * 创建支付宝支付订单
     * 
     * 当前仅支持模拟模式，返回模拟的支付链接。
     * 真实支付功能待实现。
     */
    @Override
    public String createOrder(String orderNo, int amountCents, String description, PayType type) throws Exception {
        if (isMockMode()) {
            log.info("[Alipay] 模拟模式：返回模拟二维码，orderNo={}", orderNo);
            return "mock://alipay/" + orderNo;
        }
        
        throw new UnsupportedOperationException("支付宝支付暂未实现，请使用微信支付");
    }

    /**
     * 验证支付宝回调签名
     * 
     * 模拟模式下直接返回验证通过。
     * 真实验签逻辑待实现。
     */
    @Override
    public boolean verifyNotify(HttpServletRequest request) {
        if (isMockMode()) {
            return true;
        }
        log.warn("[Alipay] 支付宝验签暂未实现");
        return false;
    }

    /**
     * 解析支付宝回调数据
     * 
     * 模拟模式下返回模拟的成功结果。
     * 真实解析逻辑待实现。
     */
    @Override
    public PaymentResult parseNotify(HttpServletRequest request) {
        if (isMockMode()) {
            PaymentResult result = new PaymentResult();
            result.setSuccess(true);
            result.setEventType("TRADE_SUCCESS");
            return result;
        }
        log.warn("[Alipay] 支付宝回调解析暂未实现");
        return null;
    }

    /**
     * 构建支付宝回调成功响应
     * 
     * 支付宝要求的成功响应格式。
     */
    @Override
    public Map<String, String> buildSuccessResponse() {
        Map<String, String> result = new HashMap<>();
        result.put("code", "10000");
        result.put("msg", "Success");
        return result;
    }

    @Override
    public boolean isMockMode() {
        return alipayConfig.getMock() == null || alipayConfig.getMock();
    }
}
