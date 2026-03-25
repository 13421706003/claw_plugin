package com.hsd.service;

import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/**
 * 微信支付服务接口
 * 
 * 提供微信支付 V3 API 的调用能力，包括：
 * - 创建 Native 支付订单
 * - 验证支付回调签名
 * - 解密回调数据
 */
public interface WechatPayService {

    /**
     * 创建微信 Native 支付订单
     * 
     * 调用微信支付 V3 API 创建 Native 扫码支付订单，
     * 返回二维码链接供前端生成二维码展示。
     * 
     * @param orderNo 商户订单号
     * @param amountCents 支付金额（单位：分）
     * @param description 商品描述
     * @return 微信支付二维码链接（weixin://wxpay/xxx）
     * @throws Exception 创建订单失败时抛出异常
     */
    String createNativeOrder(String orderNo, int amountCents, String description) throws Exception;

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
    boolean verifyNotify(String timestamp, String nonce, String body, String signature);

    /**
     * 解析微信支付回调数据
     * 
     * 解密微信支付回调通知中的加密数据，
     * 返回包含订单支付信息的 JSON 对象。
     * 
     * @param body 回调请求体的原始字符串
     * @return 解密后的支付信息 JSON，解析失败返回 null
     */
    JSONObject parseNotifyBody(String body);

    /**
     * 构建回调成功响应
     * 
     * 微信支付要求商户在处理完回调后返回特定格式的成功响应。
     * 
     * @return 成功响应的 Map 对象
     */
    Map<String, String> buildSuccessResponse();

    /**
     * 判断是否为模拟模式
     * 
     * 模拟模式下不会真实调用微信支付 API，
     * 而是返回模拟的支付二维码供开发测试使用。
     * 
     * @return 模拟模式返回 true，否则返回 false
     */
    boolean isMockMode();
}
