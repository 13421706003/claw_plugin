package com.hsd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.config.WechatPayConfig;
import com.hsd.service.WechatPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 微信支付服务实现类
 * 
 * 实现微信支付 V3 API 的调用逻辑，
 * 使用 OkHttp 发起 HTTP 请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatPayServiceImpl implements WechatPayService {

    private final WechatPayConfig wechatPayConfig;
    private final OkHttpClient httpClient = new OkHttpClient();

    /**
     * 创建微信 Native 支付订单
     * 
     * 调用微信支付 V3 API 创建 Native 扫码支付订单，
     * 返回二维码链接供前端生成二维码展示。
     * 模拟模式下返回模拟二维码链接。
     * 
     * @param orderNo 商户订单号
     * @param amountCents 支付金额（单位：分）
     * @param description 商品描述
     * @return 微信支付二维码链接（weixin://wxpay/xxx）或模拟链接
     * @throws Exception 创建订单失败时抛出异常
     */
    @Override
    public String createNativeOrder(String orderNo, int amountCents, String description) throws Exception {
        if (isMockMode()) {
            log.info("[WechatPay] 模拟模式：返回模拟二维码，orderNo={}", orderNo);
            return "mock://pay/" + orderNo;
        }
        
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

    /**
     * 验证微信支付回调签名
     * 
     * 使用 API V3 密钥验证微信支付回调请求的签名，
     * 确保回调请求来自微信支付服务器。
     * 
     * @param timestamp 回调请求头中的时间戳
     * @param nonce 回调请求头中的随机串
     * @param body 回调请求体
     * @param signature 回调请求头中的签名
     * @return 验签成功返回 true，失败返回 false
     */
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
     * 解析微信支付回调数据
     * 
     * 解密微信支付回调通知中的加密数据，
     * 返回包含订单支付信息的 JSON 对象。
     * 
     * @param body 回调请求体的原始字符串
     * @return 解密后的支付信息 JSON，解析失败返回 null
     */
    @Override
    public JSONObject parseNotifyBody(String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            JSONObject resource = json.getJSONObject("resource");
            String ciphertext = resource.getString("ciphertext");
            String nonce = resource.getString("nonce");
            String associatedData = resource.getString("associated_data");
            
            String decrypted = decryptAES256GCM(ciphertext, nonce, associatedData);
            return JSON.parseObject(decrypted);
        } catch (Exception e) {
            log.error("[WechatPay] 解析通知失败", e);
            return null;
        }
    }

    /**
     * 构建回调成功响应
     * 
     * 微信支付要求商户在处理完回调后返回特定格式的成功响应。
     * 
     * @return 成功响应的 Map 对象
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
     * 模拟模式下不会真实调用微信支付 API，
     * 而是返回模拟的支付二维码供测试使用。
     * 
     * @return 模拟模式返回 true，否则返回 false
     */
    @Override
    public boolean isMockMode() {
        return wechatPayConfig.getMock() != null && wechatPayConfig.getMock();
    }

    /**
     * 使用 AES-256-GCM 解密数据
     * 
     * 微信支付 V3 回调数据使用 AES-256-GCM 加密，
     * 此方法使用 API V3 密钥进行解密。
     * 
     * @param ciphertext Base64 编码的密文
     * @param nonce 加密使用的随机串
     * @param associatedData 附加数据
     * @return 解密后的明文字符串
     * @throws Exception 解密失败时抛出异常
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
     * 生成请求签名
     * 
     * 按照微信支付 V3 签名规范生成签名，
     * 用于 API 请求的身份验证。
     * 
     * @param method HTTP 方法
     * @param path 请求路径
     * @param timestamp 时间戳
     * @param nonce 随机串
     * @param body 请求体
     * @return Base64 编码的签名字符串
     * @throws Exception 签名生成失败时抛出异常
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
     * 构建 Authorization 请求头
     * 
     * 按照微信支付 V3 规范构建 Authorization 头，
     * 包含商户ID、随机串、签名等信息。
     * 
     * @param timestamp 时间戳
     * @param nonce 随机串
     * @param signature 签名
     * @return Authorization 头的完整字符串
     */
    private String buildAuthorization(String timestamp, String nonce, String signature) {
        return String.format(
            "WECHATPAY2-SHA256-RSA2048 mchid=\"%s\",nonce_str=\"%s\",signature=\"%s\",timestamp=\"%s\",serial_no=\"%s\"",
            wechatPayConfig.getMchId(), nonce, signature, timestamp, "your-serial-no"
        );
    }
}
