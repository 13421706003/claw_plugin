package com.hsd.service;

import com.hsd.service.dto.PaymentResult;
import com.hsd.service.enums.PayType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 通用支付服务接口
 * 
 * 定义支付渠道的统一抽象，支持多渠道支付的扩展。
 * 各支付渠道（微信、支付宝等）需实现此接口，提供具体的支付逻辑。
 * 
 * 设计模式：策略模式
 * - 通过 getChannel() 返回渠道标识，用于服务路由
 * - 统一的回调处理接口，屏蔽各渠道差异
 */
public interface PaymentService {
    
    /**
     * 获取支付渠道标识
     * 
     * @return 渠道标识码（如 "wechat"、"alipay"）
     */
    String getChannel();
    
    /**
     * 创建支付订单
     * 
     * 根据支付方式调用对应的支付API创建订单。
     * 
     * @param orderNo 商户订单号
     * @param amountCents 支付金额（单位：分）
     * @param description 商品描述
     * @param type 支付方式
     * @return 支付凭证（二维码链接、H5跳转链接等）
     * @throws Exception 创建订单失败时抛出异常
     */
    String createOrder(String orderNo, int amountCents, String description, PayType type) throws Exception;
    
    /**
     * 验证支付回调签名
     * 
     * 验证回调请求是否来自真实的支付渠道服务器，防止伪造攻击。
     * 
     * @param request HTTP请求对象（包含headers和body）
     * @return 验签成功返回 true，失败返回 false
     */
    boolean verifyNotify(HttpServletRequest request);
    
    /**
     * 解析支付回调数据
     * 
     * 将各渠道不同格式的回调数据转换为统一的 PaymentResult 对象。
     * 
     * @param request HTTP请求对象
     * @return 统一格式的支付结果，解析失败返回 null
     */
    PaymentResult parseNotify(HttpServletRequest request);
    
    /**
     * 构建回调成功响应
     * 
     * 各支付渠道要求的成功响应格式不同，需分别实现。
     * 
     * @return 渠道要求的成功响应数据
     */
    Map<String, String> buildSuccessResponse();
    
    /**
     * 判断是否为模拟模式
     * 
     * 模拟模式下不真实调用支付API，返回模拟数据用于开发测试。
     * 
     * @return 模拟模式返回 true，生产模式返回 false
     */
    boolean isMockMode();
}
