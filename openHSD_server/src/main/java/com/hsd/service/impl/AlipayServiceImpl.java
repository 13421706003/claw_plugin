package com.hsd.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.hsd.config.AlipayConfig;
import com.hsd.service.AlipayService;
import com.hsd.dto.PaymentResult;
import com.hsd.enums.PayType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付服务实现类
 * 
 * 支持创建支付宝支付订单与异步回调验签/解析。
 */
@Slf4j
@Service("aliPayService")
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

        if (type == PayType.JSAPI) {
            throw new UnsupportedOperationException("暂不支持支付宝 JSAPI 支付方式");
        }
        if (type == PayType.APP) {
            throw new UnsupportedOperationException("暂不支持支付宝 APP 支付方式");
        }

        String amountCny = new BigDecimal(amountCents)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .toPlainString();

        AlipayClient alipayClient = buildClient();
        if (type == PayType.NATIVE) {
            return createNativeOrder(alipayClient, orderNo, amountCny, description);
        }
        return createWapOrder(alipayClient, orderNo, amountCny, description);
    }

    /**
     * 验证支付宝回调签名
     * 
     * 模拟模式下直接返回验证通过（便于开发联调）。
     */
    @Override
    public boolean verifyNotify(HttpServletRequest request) {
        if (isMockMode()) {
            return true;
        }

        try {
            Map<String, String> params = getStringMap(request);
            return AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
        } catch (Exception e) {
            log.error("[Alipay] 支付宝验签失败", e);
            return false;
        }
    }

    /**
     * 解析支付宝回调数据
     * 
     * 模拟模式下仍会尝试从请求参数补齐必要字段。
     */
    @Override
    public PaymentResult parseNotify(HttpServletRequest request) {
        if (isMockMode()) {
            Map<String, String> params = getStringMap(request);
            return buildPaymentResult(params, true);
        }

        try {
            Map<String, String> params = getStringMap(request);
            return buildPaymentResult(params, false);
        } catch (Exception e) {
            log.error("[Alipay] 支付宝回调解析失败", e);
            return null;
        }
    }

    /**
     * 构建支付宝回调成功响应
     * 
     * 支付宝要求的成功响应格式。
     */
    @Override
    public Map<String, String> buildSuccessResponse() {
        Map<String, String> result = new HashMap<>();
        // 支付宝只要求“成功”，但 openHSD_server 的回调接口返回的是 JSON Map。
        // 为提高被识别概率，这里将 msg 设置为 "success"（包含关键字）
        result.put("code", "10000");
        result.put("msg", "success");
        return result;
    }

    @Override
    public boolean isMockMode() {
        return alipayConfig.getMock() == null || alipayConfig.getMock();
    }

    private static Map<String, String> getStringMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();

        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";

            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }

            params.put(name, valueStr);
        }

        return params;
    }

    private static String formatPaidAt(String gmtPayment) {
        // Alipay 回调里的 gmt_payment 通常是 "yyyy-MM-dd HH:mm:ss"
        if (gmtPayment == null || gmtPayment.isEmpty()) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }

        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(gmtPayment);
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
        } catch (Exception e) {
            // 兼容异常格式：解析失败则使用当前时间
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }
    }

    private PaymentResult buildPaymentResult(Map<String, String> params, boolean isMock) {
        if (!isMock) {
            String appId = params.get("app_id");
            if (appId == null || !appId.equals(alipayConfig.getAppId())) {
                log.error("[Alipay] 回调 app_id 不匹配, app_id={}, expected={}", appId, alipayConfig.getAppId());
                return null;
            }
            String sellerId = alipayConfig.getSellerId();
            if (sellerId != null && !sellerId.isBlank()) {
                String notifySellerId = params.get("seller_id");
                if (!sellerId.equals(notifySellerId)) {
                    log.error("[Alipay] 回调 seller_id 不匹配, seller_id={}, expected={}", notifySellerId, sellerId);
                    return null;
                }
            }
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no"); // 支付宝交易号
        String tradeStatus = params.get("trade_status");
        String gmtPayment = params.get("gmt_payment");

        if (!isMock && !"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            // 非支付成功事件，交由调用方忽略
            return null;
        }

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setEventType(tradeStatus != null ? tradeStatus : "TRADE_SUCCESS");
        result.setOrderNo(outTradeNo);
        result.setChannelOrderId(tradeNo);
        result.setPaidAt(formatPaidAt(gmtPayment));

        // total_amount 是字符串，单位：元；转成分
        String totalAmount = params.get("total_amount");
        if (totalAmount != null && !totalAmount.isEmpty()) {
            BigDecimal amount = new BigDecimal(totalAmount);
            result.setAmountCents(amount.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue());
        }

        return result;
    }

    private AlipayClient buildClient() {
        String privateKey = alipayConfig.getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("支付宝私钥未配置（privateKey 或 merchantPrivateKey）");
        }
        return new DefaultAlipayClient(
                alipayConfig.getServerUrl(),
                alipayConfig.getAppId(),
                privateKey,
                "json",
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );
    }

    private String createNativeOrder(AlipayClient client, String orderNo, String amountCny, String description)
            throws AlipayApiException {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", amountCny);
        bizContent.put("subject", description);
        bizContent.put("timeout_express", alipayConfig.getTimeout());
        bizContent.put("product_code", "FACE_TO_FACE_PAYMENT");
        request.setBizContent(bizContent.toString());

        AlipayTradePrecreateResponse response = client.execute(request);
        if (response == null || !response.isSuccess() || response.getQrCode() == null || response.getQrCode().isBlank()) {
            String subMsg = response == null ? "response is null"
                    : (response.getSubMsg() == null ? response.getMsg() : response.getSubMsg());
            throw new AlipayApiException("支付宝预下单失败: " + subMsg);
        }
        log.info("[Alipay] 预下单成功: out_trade_no={}, qr_code={}", orderNo, response.getQrCode());
        return response.getQrCode();
    }

    private String createWapOrder(AlipayClient client, String orderNo, String amountCny, String description)
            throws AlipayApiException {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", amountCny);
        bizContent.put("subject", description);
        bizContent.put("product_code", "QUICK_WAP_PAY");
        bizContent.put("timeout_express", alipayConfig.getTimeout());
        request.setBizContent(bizContent.toString());

        log.info("[Alipay] 创建WAP订单: out_trade_no={}, total_amount={}, subject={}", orderNo, amountCny, description);
        return client.pageExecute(request).getBody();
    }
}
