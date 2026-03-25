package com.hsd.service;

import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;

/**
 * OpenRouter API 服务接口
 * 
 * 提供 OpenRouter 平台的 API 调用能力，包括：
 * - 获取 API Key 信息
 * - 查询账户额度
 * - 更新 API Key 限额
 * - 验证 API Key 有效性
 */
public interface OpenRouterService {

    /**
     * 获取指定 API Key 的详细信息
     * 
     * 通过 Management API 根据 Key Hash 获取 Key 的详细信息，
     * 包括限额、使用量、状态等。
     * 
     * @param keyHash API Key 的 Hash 值（不含 sk-or- 前缀）
     * @return Key 信息 JSON 对象，失败返回 null
     * @throws IOException 网络请求异常
     */
    JSONObject getKeyInfo(String keyHash) throws IOException;

    /**
     * 获取当前 API Key 的信息
     * 
     * 使用 API Key 本身调用接口获取其信息，用于验证 Key 的有效性。
     * 
     * @param apiKey 完整的 API Key
     * @return Key 信息 JSON 对象，失败返回 null
     * @throws IOException 网络请求异常
     */
    JSONObject getCurrentKeyInfo(String apiKey) throws IOException;

    /**
     * 获取账户额度信息
     * 
     * 查询 OpenRouter 账户的总充值额度和已使用额度。
     * 
     * @param apiKey API Key
     * @return 额度信息 JSON 对象，包含 total_credits 和 total_usage
     * @throws IOException 网络请求异常
     */
    JSONObject getCredits(String apiKey) throws IOException;

    /**
     * 更新 API Key 的消费限额
     * 
     * 通过 Management API 更新指定 Key 的消费上限。
     * 充值成功后调用此方法增加用户的可用额度。
     * 
     * @param keyHash API Key 的 Hash 值
     * @param newLimit 新的消费限额（美元）
     * @return 更新成功返回 true，失败返回 false
     * @throws IOException 网络请求异常
     */
    boolean updateKeyLimit(String keyHash, double newLimit) throws IOException;

    /**
     * 验证 API Key 有效性并提取 Key Hash
     * 
     * 通过调用 OpenRouter API 验证 Key 是否有效，
     * 如果有效则从中提取 Key Hash 用于后续管理操作。
     * 
     * @param apiKey 完整的 API Key
     * @return Key Hash 值，无效 Key 返回 null
     * @throws IOException 网络请求异常
     */
    String verifyAndGetKeyHash(String apiKey) throws IOException;

    /**
     * 获取所有 API Key 列表
     * 
     * 通过 Management API 获取账户下所有 API Key 的列表。
     * 
     * @return Key 列表 JSON 对象，失败返回 null
     * @throws IOException 网络请求异常
     */
    JSONObject listKeys() throws IOException;
}
