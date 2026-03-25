package com.hsd.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 充值业务服务接口
 * 
 * 核心业务逻辑接口，负责协调微信支付和 OpenRouter API，
 * 实现完整的充值流程：
 * 1. API Key 绑定与管理
 * 2. 创建充值订单
 * 3. 处理支付回调
 * 4. 分配额度到 OpenRouter
 */
public interface RechargeService {

    /**
     * 获取用户绑定的 OpenRouter API Key 信息
     * 
     * 查询用户是否已绑定 API Key，如果已绑定则从 OpenRouter
     * 获取该 Key 的限额、使用量等信息。
     * 
     * @param userId 用户ID
     * @return 包含绑定状态和 Key 信息的响应 Map
     */
    Map<String, Object> getKeyInfo(Long userId);

    /**
     * 绑定 OpenRouter API Key
     * 
     * 验证用户提供的 API Key 有效性，提取 Key Hash 并保存到数据库。
     * 绑定后用户才能进行充值操作。
     * 
     * @param userId 用户ID
     * @param apiKey 用户提供的完整 API Key
     * @return 绑定结果响应 Map
     */
    Map<String, Object> bindKey(Long userId, String apiKey);

    /**
     * 创建充值订单
     * 
     * 完整的创建订单流程：
     * 1. 验证用户状态和绑定信息
     * 2. 计算人民币金额
     * 3. 生成唯一订单号
     * 4. 调用微信支付创建 Native 订单
     * 5. 保存订单到数据库
     * 
     * @param userId 用户ID
     * @param amountUsd 充值金额（美元）
     * @return 包含订单信息和二维码的响应 Map
     */
    Map<String, Object> createOrder(Long userId, BigDecimal amountUsd);

    /**
     * 查询订单状态
     * 
     * 根据订单号查询订单的详细信息和当前状态，
     * 用于前端轮询支付结果。
     * 
     * @param orderNo 商户订单号
     * @return 包含订单状态信息的响应 Map
     */
    Map<String, Object> getOrderStatus(String orderNo);

    /**
     * 处理支付成功回调
     * 
     * 微信支付成功后的核心处理逻辑：
     * 1. 更新订单状态为已支付
     * 2. 查询当前 Key 的限额
     * 3. 计算新限额并更新到 OpenRouter
     * 4. 更新订单状态为已完成
     * 
     * @param orderNo 商户订单号
     * @param wechatOrderId 微信支付订单号
     * @param paidAtStr 支付完成时间字符串
     */
    void handlePaySuccess(String orderNo, String wechatOrderId, String paidAtStr);

    /**
     * 获取用户的充值历史记录
     * 
     * 查询用户的充值订单列表，按时间倒序排列，
     * 用于前端展示充值记录。
     * 
     * @param userId 用户ID
     * @param limit 返回记录数量限制
     * @return 包含订单列表的响应 Map
     */
    Map<String, Object> getOrderHistory(Long userId, int limit);

    /**
     * 获取当前汇率
     * 
     * @return 美元兑人民币汇率
     */
    BigDecimal getExchangeRate();

    /**
     * 模拟支付成功
     * 
     * 仅在模拟模式下可用，手动触发支付成功流程。
     * 用于开发测试阶段模拟支付回调。
     * 
     * @param orderNo 商户订单号
     * @return 处理结果响应 Map
     */
    Map<String, Object> mockPaySuccess(String orderNo);
}
