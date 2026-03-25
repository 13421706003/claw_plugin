package com.hsd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.config.WechatPayConfig;
import com.hsd.service.WechatPayService;
import com.hsd.service.dto.PaymentResult;
import com.hsd.service.enums.PayType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service("wechatPayService")
@RequiredArgsConstructor
public class WechatPayServiceImpl implements WechatPayService {

    private final WechatPayConfig wechatPayConfig;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public String getChannel() {
        return "wechat";
    }

    @Override
    public String createOrder(String orderNo, int amountCents, String description, PayType type) throws Exception {
        if (isMockMode()) {
            log.info("[WechatPay] 模拟模式：返回模拟二维码，orderNo={}", orderNo);
            return "mock://pay/" + orderNo;
        }
        
        switch (type) {
            case NATIVE:
                return createNativeOrderInternal(orderNo, amountCents, description);
            case H5:
            case APP:
            case JSAPI:
                throw new UnsupportedOperationException("暂不支持的支付方式: " + type);
            default:
                return createNativeOrderInternal(orderNo, amountCents, description);
        }
    }

    private String createNativeOrderInternal(String orderNo, int amountCents, String description) throws Exception {
        String url = "https://api.mch.weixin.qq.com/v3/pay/transactions/native";
        
        JSONObject body = new JSONObject();
        body.put("appid", wechatPayConfig.getAppId());
        body.put("mchid", wechatPayConfig.getMchId());
        body.put("description", description);
        body.put("out_trade_no", orderNo);
        body.put("notify_url", wechatPayConfig.getNotifyUrl());
        
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

    @Override
    public Map<String, String> buildSuccessResponse() {
        Map<String, String> result = new HashMap<>();
        result.put("code", "SUCCESS");
        result.put("message", "成功");
        return result;
    }

    @Override
    public boolean isMockMode() {
        return wechatPayConfig.getMock() != null && wechatPayConfig.getMock();
    }

    @Deprecated
    @Override
    public String createNativeOrder(String orderNo, int amountCents, String description) throws Exception {
        return createOrder(orderNo, amountCents, description, PayType.NATIVE);
    }

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

    private String generateSignature(String method, String path, String timestamp, String nonce, String body) throws Exception {
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(wechatPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private String buildAuthorization(String timestamp, String nonce, String signature) {
        return String.format(
            "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",signature=\"%s\",timestamp=\"%s\",serial_no=\"%s\"",
            wechatPayConfig.getMchId(), nonce, signature, timestamp, "your-serial-no"
        );
    }
}
