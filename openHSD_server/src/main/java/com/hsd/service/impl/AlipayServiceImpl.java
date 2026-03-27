package com.hsd.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeAppPayRequest;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支付宝支付服务实现类
 * 
 * 支持创建支付宝支付订单与异步回调验签/解析。
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

        // 根据支付方式路由到对应支付宝接口：
        // - NATIVE -> alipay.trade.precreate（返回 qr_code）
        // - H5     -> alipay.trade.wap.pay（返回 HTML form）
        // - APP    -> alipay.trade.app.pay（返回 orderString）
        switch (type) {
            case NATIVE:
                return createPrecreateOrder(orderNo, amountCents, description);
            case H5:
                return createWapOrderUrl(orderNo, amountCents, description);
            case APP:
                return createAppOrder(orderNo, amountCents, description);
            case JSAPI:
                // JSAPI 需要额外的 openid 等参数，当前模型暂不支持。
                throw new UnsupportedOperationException("暂不支持支付宝 JSAPI 支付方式");
            default:
                return createWapOrderUrl(orderNo, amountCents, description);
        }
    }

    private String createPrecreateOrder(String orderNo, int amountCents, String description) throws AlipayApiException {
        String amountCny = new BigDecimal(amountCents)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .toPlainString();

        AlipayClient alipayClient = new DefaultAlipayClient(
                alipayConfig.getServerUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );

        AlipayTradePrecreateRequest alipayRequest = new AlipayTradePrecreateRequest();
        alipayRequest.setNotifyUrl(alipayConfig.getNotifyUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", amountCny);
        bizContent.put("subject", description);
        alipayRequest.setBizContent(bizContent.toString());

        try {
            log.info("[Alipay] 创建支付宝扫码预下单: out_trade_no={}, total_amount={}, subject={}",
                    orderNo, amountCny, description);
            AlipayTradePrecreateResponse response = alipayClient.execute(alipayRequest);
            log.info("[Alipay] 预下单响应: code={}, msg={}, subCode={}, subMsg={}",
                    response != null ? response.getCode() : null,
                    response != null ? response.getMsg() : null,
                    response != null ? response.getSubCode() : null,
                    response != null ? response.getSubMsg() : null);
            if (response == null || !response.isSuccess()) {
                String code = response != null ? response.getCode() : "null";
                String msg = response != null ? response.getMsg() : "response is null";
                String subCode = response != null ? response.getSubCode() : "null";
                String subMsg = response != null ? response.getSubMsg() : "response is null";
                String body = response != null ? response.getBody() : "response is null";
                log.error("[Alipay] 预下单失败详情: out_trade_no={}, code={}, msg={}, subCode={}, subMsg={}, body={}",
                        orderNo, code, msg, subCode, subMsg, body);
                throw new AlipayApiException("支付宝预下单失败: code=" + code + ", subCode=" + subCode + ", subMsg=" + subMsg);
            }
            String qrCode = response.getQrCode();
            if (qrCode == null || qrCode.isEmpty()) {
                log.error("[Alipay] 预下单成功但二维码为空: out_trade_no={}, body={}", orderNo, response.getBody());
                throw new AlipayApiException("支付宝预下单成功但未返回二维码");
            }
            log.info("[Alipay] 预下单成功: out_trade_no={}, qr_code={}", orderNo, qrCode);
            return qrCode;
        } catch (AlipayApiException e) {
            log.error("[Alipay] 创建扫码预下单失败", e);
            throw e;
        }
    }

    private String createWapOrder(String orderNo, int amountCents, String description) throws AlipayApiException {
        String amountCny = new BigDecimal(amountCents)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .toPlainString();

        // 1、创建支付宝客户端
        AlipayClient alipayClient = new DefaultAlipayClient(
                alipayConfig.getServerUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );

        // 2、创建支付请求（WAP 支付会返回一个可直接在页面提交的 form/HTML）
        AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();
        alipayRequest.setNotifyUrl(alipayConfig.getNotifyUrl());
        alipayRequest.setReturnUrl(alipayConfig.getReturnUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);        // 我们自己生成的订单编号
        bizContent.put("total_amount", amountCny);    // 订单的总金额（单位：元，保留两位小数）
        bizContent.put("subject", description);       // 支付名称
        // 官方手机网站支付产品码（alipay.trade.wap.pay）
        bizContent.put("product_code", "QUICK_WAP_WAY");
        bizContent.put("timeout_express", alipayConfig.getTimeout());

        alipayRequest.setBizContent(bizContent.toString());

        try {
            log.info("[Alipay] 创建支付宝WAP订单: out_trade_no={}, total_amount={}, subject={}",
                    orderNo, amountCny, description);
            return alipayClient.pageExecute(alipayRequest).getBody();
        } catch (AlipayApiException e) {
            log.error("[Alipay] 创建WAP订单失败", e);
            throw e;
        }
    }

    /**
     * 创建支付宝WAP支付链接（返回URL，不返回HTML）
     */
    private String createWapOrderUrl(String orderNo, int amountCents, String description) throws AlipayApiException {
        String amountCny = new BigDecimal(amountCents)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .toPlainString();

        AlipayClient alipayClient = new DefaultAlipayClient(
                alipayConfig.getServerUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );

        AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();
        alipayRequest.setNotifyUrl(alipayConfig.getNotifyUrl());
        alipayRequest.setReturnUrl(alipayConfig.getReturnUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", amountCny);
        bizContent.put("subject", description);
        bizContent.put("product_code", "QUICK_WAP_WAY");
        bizContent.put("timeout_express", alipayConfig.getTimeout());
        alipayRequest.setBizContent(bizContent.toString());

        try {
            log.info("[Alipay] 创建支付宝WAP支付URL: out_trade_no={}, total_amount={}, subject={}",
                    orderNo, amountCny, description);
            String body = alipayClient.pageExecute(alipayRequest, "GET").getBody();
            return extractWapPayUrl(body);
        } catch (AlipayApiException e) {
            log.error("[Alipay] 创建WAP支付URL失败", e);
            throw e;
        }
    }

    /**
     * 兼容 SDK 返回 URL 或 form 两种场景，统一提取可直接跳转的 URL。
     */
    private String extractWapPayUrl(String body) {
        if (body == null || body.isBlank()) {
            throw new RuntimeException("支付宝返回内容为空，无法提取支付URL");
        }

        String trimmed = body.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        Matcher actionMatcher = Pattern.compile("action=\"([^\"]+)\"").matcher(trimmed);
        if (!actionMatcher.find()) {
            throw new RuntimeException("支付宝返回内容格式异常，未找到支付URL");
        }
        String actionUrl = actionMatcher.group(1);

        // 如果是 form 模式，把 hidden 字段拼接到 action 查询串，确保前端拿到的是完整可跳转 URL。
        Matcher inputMatcher = Pattern.compile("name=\"([^\"]+)\"\\s+value=\"([^\"]*)\"").matcher(trimmed);
        StringBuilder queryBuilder = new StringBuilder();
        while (inputMatcher.find()) {
            if (queryBuilder.length() > 0) {
                queryBuilder.append("&");
            }
            queryBuilder.append(URLEncoder.encode(inputMatcher.group(1), StandardCharsets.UTF_8));
            queryBuilder.append("=");
            queryBuilder.append(URLEncoder.encode(inputMatcher.group(2), StandardCharsets.UTF_8));
        }

        if (queryBuilder.length() == 0) {
            return actionUrl;
        }
        return actionUrl + (actionUrl.contains("?") ? "&" : "?") + queryBuilder;
    }

    private String createAppOrder(String orderNo, int amountCents, String description) throws AlipayApiException {
        String amountCny = new BigDecimal(amountCents)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .toPlainString();

        AlipayClient alipayClient = new DefaultAlipayClient(
                alipayConfig.getServerUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );

        AlipayTradeAppPayRequest alipayRequest = new AlipayTradeAppPayRequest();
        alipayRequest.setNotifyUrl(alipayConfig.getNotifyUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", amountCny);
        bizContent.put("subject", description);
        // 官方 APP 支付产品码（alipay.trade.app.pay）
        bizContent.put("product_code", "QUICK_MSECURITY_PAY");
        bizContent.put("timeout_express", alipayConfig.getTimeout());
        alipayRequest.setBizContent(bizContent.toString());

        try {
            log.info("[Alipay] 创建支付宝APP订单: out_trade_no={}, total_amount={}, subject={}",
                    orderNo, amountCny, description);
            // APP 支付返回 orderString，交给客户端拉起支付宝 SDK。
            return alipayClient.sdkExecute(alipayRequest).getBody();
        } catch (AlipayApiException e) {
            log.error("[Alipay] 创建APP订单失败", e);
            throw e;
        }
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
}
