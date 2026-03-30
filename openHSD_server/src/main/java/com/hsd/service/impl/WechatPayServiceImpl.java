package com.hsd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.config.WechatPayConfig;
import com.hsd.service.WechatPayService;
import com.hsd.dto.PaymentResult;
import com.hsd.enums.PayType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 微信支付服务实现类
 * 
 * 实现微信支付 V3 API 的对接，支持 Native 扫码支付
 */
@Slf4j
@Service("wechatPayService")
@RequiredArgsConstructor
public class WechatPayServiceImpl implements WechatPayService {

    private final WechatPayConfig wechatPayConfig;
    private final OkHttpClient httpClient = new OkHttpClient();

    /**
     * 获取支付渠道标识
     * 
     * @return 渠道标识 "wechat"
     */
    @Override
    public String getChannel() {
        return "wechat";
    }

    /**
     * 创建支付订单
     * 
     * @param orderNo 商户订单号
     * @param amountCents 金额（分）
     * @param description 订单描述
     * @param type 支付类型（NATIVE、H5等）
     * @return 支付二维码链接或跳转链接
     * @throws Exception 创建订单失败时抛出异常
     */
    @Override
    public String createOrder(String orderNo, int amountCents, String description, PayType type) throws Exception {
        return createOrder(orderNo, amountCents, description, type, null);
    }
    
    /**
     * 创建支付订单（支持客户端IP）
     * 
     * @param orderNo 商户订单号
     * @param amountCents 金额（分）
     * @param description 订单描述
     * @param type 支付类型（NATIVE、H5等）
     * @param clientIp 客户端IP地址（H5支付必需）
     * @return 支付二维码链接或跳转链接
     * @throws Exception 创建订单失败时抛出异常
     */
    @Override
    public String createOrder(String orderNo, int amountCents, String description, PayType type, String clientIp) throws Exception {
        if (isMockMode()) {
            log.info("[WechatPay] 模拟模式：返回模拟支付链接，orderNo={}", orderNo);
            return "mock://pay/" + orderNo;
        }
        
        switch (type) {
            case NATIVE:
                return createNativeOrderInternal(orderNo, amountCents, description);
            case H5:
                return createH5OrderInternal(orderNo, amountCents, description, clientIp);
            case APP:
            case JSAPI:
                throw new UnsupportedOperationException("暂不支持的支付方式: " + type);
            default:
                return createNativeOrderInternal(orderNo, amountCents, description);
        }
    }

    /**
     * 内部方法：创建 Native 扫码支付订单
     * 
     * 设置订单过期时间为 15 分钟，系统会在 10 分钟时主动关闭订单。
     */
    private String createNativeOrderInternal(String orderNo, int amountCents, String description) throws Exception {
        String url = "https://api.mch.weixin.qq.com/v3/pay/transactions/native";
        
        JSONObject body = new JSONObject();
        body.put("appid", wechatPayConfig.getAppId());
        body.put("mchid", wechatPayConfig.getMchId());
        body.put("description", description);
        body.put("out_trade_no", orderNo);
        body.put("notify_url", wechatPayConfig.getNotifyUrl());
        
        // 设置订单过期时间（15分钟后，给系统10分钟关单留缓冲）
        String timeExpire = ZonedDateTime.now(ZoneOffset.of("+8"))
            .plusMinutes(15)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        body.put("time_expire", timeExpire);
        
        JSONObject amount = new JSONObject();
        amount.put("total", amountCents);
        amount.put("currency", "CNY");
        body.put("amount", amount);

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String bodyStr = body.toJSONString();

        String signature = generateSignature("POST", "/v3/pay/transactions/native", timestamp, nonceStr, bodyStr);
        String authorization = buildAuthorization(timestamp, nonceStr, signature);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", authorization)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(bodyStr, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body().string();
            log.info("[WechatPay] 创建Native订单响应: {}", respBody);
            
            if (!response.isSuccessful()) {
                log.error("[WechatPay] 创建订单失败: {}", respBody);
                throw new RuntimeException("创建支付订单失败: " + respBody);
            }

            JSONObject result = JSON.parseObject(respBody);
            return result.getString("code_url");
        }
    }

    /**
     * 内部方法：创建 H5 支付订单
     * 
     * 用于手机移动端微信支付，返回跳转链接。
     * 
     * @param orderNo 商户订单号
     * @param amountCents 金额（分）
     * @param description 订单描述
     * @param clientIp 客户端IP地址
     * @return H5支付跳转链接
     */
    private String createH5OrderInternal(String orderNo, int amountCents, String description, String clientIp) throws Exception {
        String url = "https://api.mch.weixin.qq.com/v3/pay/transactions/h5";
        
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = "127.0.0.1";
        }
        
        JSONObject body = new JSONObject();
        body.put("appid", wechatPayConfig.getAppId());
        body.put("mchid", wechatPayConfig.getMchId());
        body.put("description", description);
        body.put("out_trade_no", orderNo);
        body.put("notify_url", wechatPayConfig.getNotifyUrl());
        
        String timeExpire = ZonedDateTime.now(ZoneOffset.of("+8"))
            .plusMinutes(15)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        body.put("time_expire", timeExpire);
        
        JSONObject amount = new JSONObject();
        amount.put("total", amountCents);
        amount.put("currency", "CNY");
        body.put("amount", amount);
        
        JSONObject sceneInfo = new JSONObject();
        sceneInfo.put("payer_client_ip", clientIp);
        JSONObject h5Info = new JSONObject();
        h5Info.put("type", "Wap");
        sceneInfo.put("h5_info", h5Info);
        body.put("scene_info", sceneInfo);

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String bodyStr = body.toJSONString();

        String signature = generateSignature("POST", "/v3/pay/transactions/h5", timestamp, nonceStr, bodyStr);
        String authorization = buildAuthorization(timestamp, nonceStr, signature);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", authorization)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(bodyStr, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body().string();
            log.info("[WechatPay] 创建H5订单响应: {}", respBody);
            
            if (!response.isSuccessful()) {
                log.error("[WechatPay] 创建H5订单失败: {}", respBody);
                throw new RuntimeException("创建H5支付订单失败: " + respBody);
            }

            JSONObject result = JSON.parseObject(respBody);
            return result.getString("h5_url");
        }
    }

    /**
     * 验证微信支付回调签名
     * 
     * @param request HTTP 请求
     * @return 验签是否通过
     */
    @Override
    public boolean verifyNotify(HttpServletRequest request) {
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signature = request.getHeader("Wechatpay-Signature");
        
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();
            
            return verifyNotify(timestamp, nonce, body, signature);
        } catch (Exception e) {
            log.error("[WechatPay] 验签失败", e);
            return false;
        }
    }

    /**
     * 解析微信支付回调数据
     * 
     * @param request HTTP 请求
     * @return 解析后的支付结果
     */
    @Override
    public PaymentResult parseNotify(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();
            
            JSONObject notifyData = parseNotifyBody(body);
            if (notifyData == null) {
                return null;
            }
            
            PaymentResult result = new PaymentResult();
            result.setSuccess(true);
            result.setEventType(notifyData.getString("event_type"));
            
            // 处理订单关闭事件
            String eventType = notifyData.getString("event_type");
            if ("TRANSACTION.CLOSED".equals(eventType)) {
                result.setClosed(true);
                JSONObject resource = notifyData.getJSONObject("resource");
                if (resource != null) {
                    result.setOrderNo(resource.getString("out_trade_no"));
                }
                return result;
            }
            
            // 支付成功事件
            JSONObject resource = notifyData.getJSONObject("resource");
            if (resource != null) {
                result.setOrderNo(resource.getString("out_trade_no"));
                result.setChannelOrderId(resource.getString("transaction_id"));
                result.setPaidAt(resource.getString("success_time"));
                JSONObject amountObj = resource.getJSONObject("amount");
                if (amountObj != null) {
                    result.setAmountCents(amountObj.getInteger("total"));
                }
            }
            
            return result;
        } catch (Exception e) {
            log.error("[WechatPay] 解析通知失败", e);
            return null;
        }
    }

    /**
     * 构建微信支付回调成功响应
     * 
     * @return 成功响应 Map
     */
    @Override
    public Map<String, String> buildSuccessResponse() {
        Map<String, String> result = new HashMap<>();
        result.put("code", "SUCCESS");
        result.put("message", "成功");
        return result;
    }

    /**
     * 判断是否为模拟模式
     * 
     * @return 是否模拟模式
     */
    @Override
    public boolean isMockMode() {
        return wechatPayConfig.getMock() != null && wechatPayConfig.getMock();
    }

    /**
     * 关闭订单
     * 
     * 主动调用微信API关闭未支付的订单。
     * 用于系统在业务超时（10分钟）后主动关闭订单。
     * 
     * @param orderNo 商户订单号
     */
    @Override
    public void closeOrder(String orderNo) {
        if (isMockMode()) {
            log.info("[WechatPay] 模拟模式：模拟关单成功，orderNo={}", orderNo);
            return;
        }
        
        String url = String.format(
            "https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/%s/close",
            orderNo
        );
        
        JSONObject body = new JSONObject();
        body.put("mchid", wechatPayConfig.getMchId());
        
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String bodyStr = body.toJSONString();
        
        String path = "/v3/pay/transactions/out-trade-no/" + orderNo + "/close";
        
        try {
            String signature = generateSignature("POST", path, timestamp, nonceStr, bodyStr);
            String authorization = buildAuthorization(timestamp, nonceStr, signature);
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", authorization)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(bodyStr, MediaType.parse("application/json")))
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body().string();
                log.info("[WechatPay] 关单响应: orderNo={}, response={}", orderNo, respBody);
                
                if (!response.isSuccessful()) {
                    log.error("[WechatPay] 关单失败: orderNo={}, response={}", orderNo, respBody);
                    throw new RuntimeException("关单失败: " + respBody);
                }
                
                log.info("[WechatPay] 关单成功: orderNo={}", orderNo);
            }
        } catch (Exception e) {
            log.error("[WechatPay] 关单异常: orderNo={}", orderNo, e);
            throw new RuntimeException("关单异常: " + e.getMessage(), e);
        }
    }

    /**
     * 申请退款
     * 
     * 当订单已关闭但用户支付成功时，调用此方法退款。
     * 
     * @param orderNo 商户订单号
     * @param channelOrderId 微信订单号
     * @param amountCents 退款金额（分）
     * @return 退款是否成功
     */
    @Override
    public boolean refund(String orderNo, String channelOrderId, int amountCents) {
        if (isMockMode()) {
            log.info("[WechatPay] 模拟模式：模拟退款成功，orderNo={}", orderNo);
            return true;
        }
        
        String url = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";
        
        JSONObject body = new JSONObject();
        body.put("out_trade_no", orderNo);
        body.put("out_refund_no", "RF" + System.currentTimeMillis());
        body.put("transaction_id", channelOrderId);
        
        JSONObject amount = new JSONObject();
        amount.put("refund", amountCents);
        amount.put("total", amountCents);
        amount.put("currency", "CNY");
        body.put("amount", amount);
        
        body.put("reason", "订单已关闭，自动退款");
        
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String bodyStr = body.toJSONString();
        
        try {
            String signature = generateSignature("POST", "/v3/refund/domestic/refunds", timestamp, nonceStr, bodyStr);
            String authorization = buildAuthorization(timestamp, nonceStr, signature);
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", authorization)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(RequestBody.create(bodyStr, MediaType.parse("application/json")))
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body().string();
                log.info("[WechatPay] 退款响应: orderNo={}, response={}", orderNo, respBody);
                
                if (!response.isSuccessful()) {
                    log.error("[WechatPay] 退款失败: orderNo={}, response={}", orderNo, respBody);
                    return false;
                }
                
                JSONObject result = JSON.parseObject(respBody);
                String status = result.getString("status");
                boolean success = "SUCCESS".equals(status) || "PROCESSING".equals(status);
                
                if (success) {
                    log.info("[WechatPay] 退款成功: orderNo={}, amountCents={}", orderNo, amountCents);
                }
                return success;
            }
        } catch (Exception e) {
            log.error("[WechatPay] 退款异常: orderNo={}", orderNo, e);
            return false;
        }
    }

    /**
     * @deprecated 请使用 {@link #createOrder} 方法
     */
    @Deprecated
    @Override
    public String createNativeOrder(String orderNo, int amountCents, String description) throws Exception {
        return createOrder(orderNo, amountCents, description, PayType.NATIVE);
    }

    /**
     * @deprecated 请使用 {@link #verifyNotify(HttpServletRequest)} 方法
     */
    @Deprecated
    @Override
    public boolean verifyNotify(String timestamp, String nonce, String body, String signature) {
        try {
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(wechatPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("[WechatPay] 验签失败", e);
            return false;
        }
    }

    /**
     * @deprecated 内部方法，请使用 {@link #parseNotify(HttpServletRequest)}
     */
    @Deprecated
    @Override
    public JSONObject parseNotifyBody(String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            JSONObject resource = json.getJSONObject("resource");
            String ciphertext = resource.getString("ciphertext");
            String nonce = resource.getString("nonce");
            String associatedData = resource.getString("associated_data");
            
            String decrypted = decryptAES256GCM(ciphertext, nonce, associatedData);
            JSONObject result = new JSONObject();
            result.put("event_type", json.getString("event_type"));
            result.put("resource", JSON.parseObject(decrypted));
            return result;
        } catch (Exception e) {
            log.error("[WechatPay] 解析通知失败", e);
            return null;
        }
    }

    /**
     * AES-256-GCM 解密
     */
    private String decryptAES256GCM(String ciphertext, String nonce, String associatedData) throws Exception {
        byte[] key = wechatPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8);
        byte[] iv = nonce.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedData = Base64.getDecoder().decode(ciphertext);
        
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        
        if (associatedData != null && !associatedData.isEmpty()) {
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        }
        
        byte[] decrypted = cipher.doFinal(encryptedData);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 生成签名
     */
    private String generateSignature(String method, String path, String timestamp, String nonce, String body) throws Exception {
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(wechatPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 构建 Authorization 头
     */
    private String buildAuthorization(String timestamp, String nonce, String signature) {
        return String.format(
            "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",signature=\"%s\",timestamp=\"%s\",serial_no=\"%s\"",
            wechatPayConfig.getMchId(), nonce, signature, timestamp, "your-serial-no"
        );
    }
}
