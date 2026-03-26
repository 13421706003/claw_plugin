package com.hsd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.config.OpenRouterConfig;
import com.hsd.service.OpenRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenRouter API 服务实现类
 * 
 * 实现 OpenRouter 平台的 API 调用逻辑，
 * 使用 OkHttp 发起 HTTP 请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterServiceImpl implements OpenRouterService {

    private final OpenRouterConfig openRouterConfig;
    private final OkHttpClient httpClient = new OkHttpClient();

    /**
     * 根据 Key Hash 获取 Key 信息
     * 
     * @param keyHash Key 的哈希值
     * @return Key 信息 JSON
     * @throws IOException 网络请求失败
     */
    @Override
    public JSONObject getKeyInfo(String keyHash) throws IOException {
        String url = openRouterConfig.getBaseUrl() + "/keys/" + keyHash;
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + openRouterConfig.getManagementKey())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("[OpenRouter] 获取Key信息失败: {}", response.code());
                return null;
            }
            String body = response.body().string();
            JSONObject result = JSON.parseObject(body);
            return result.getJSONObject("data");
        }
    }

    /**
     * 获取当前 API Key 的信息
     * 
     * @param apiKey API Key
     * @return Key 信息 JSON
     * @throws IOException 网络请求失败
     */
    @Override
    public JSONObject getCurrentKeyInfo(String apiKey) throws IOException {
        String url = openRouterConfig.getBaseUrl() + "/key";
        
        log.info("[OpenRouter] 请求Key信息: url={}, keyPrefix={}", url, 
                 apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : "null");
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            log.info("[OpenRouter] 响应: code={}, body={}", response.code(), respBody);
            
            if (!response.isSuccessful()) {
                log.error("[OpenRouter] 获取当前Key信息失败: code={}, body={}", response.code(), respBody);
                return null;
            }
            JSONObject result = JSON.parseObject(respBody);
            return result.getJSONObject("data");
        }
    }

    /**
     * 获取账户额度信息
     * 
     * @param apiKey API Key
     * @return 额度信息 JSON
     * @throws IOException 网络请求失败
     */
    @Override
    public JSONObject getCredits(String apiKey) throws IOException {
        String url = openRouterConfig.getBaseUrl() + "/credits";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("[OpenRouter] 获取额度失败: {}", response.code());
                return null;
            }
            String body = response.body().string();
            JSONObject result = JSON.parseObject(body);
            return result.getJSONObject("data");
        }
    }

    /**
     * 更新 Key 的额度限制
     * 
     * @param keyHash Key 的哈希值
     * @param newLimit 新的额度限制
     * @return 是否更新成功
     * @throws IOException 网络请求失败
     */
    @Override
    public boolean updateKeyLimit(String keyHash, double newLimit) throws IOException {
        String url = openRouterConfig.getBaseUrl() + "/keys/" + keyHash;
        
        log.info("[OpenRouter] 更新Key额度请求: url={}, keyHash={}, newLimit={}", url, keyHash, newLimit);
        
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("limit", newLimit);
        
        RequestBody body = RequestBody.create(
                JSON.toJSONString(bodyMap),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + openRouterConfig.getManagementKey())
                .addHeader("Content-Type", "application/json")
                .patch(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            log.info("[OpenRouter] 更新Key额度响应: code={}, body={}", response.code(), respBody);
            
            if (!response.isSuccessful()) {
                log.error("[OpenRouter] 更新Key额度失败: code={}, body={}", response.code(), respBody);
                return false;
            }
            log.info("[OpenRouter] 成功更新Key额度: keyHash={}, newLimit={}", keyHash, newLimit);
            return true;
        }
    }

    /**
     * 验证 API Key 并获取 Key Hash
     * 
     * @param apiKey API Key
     * @return Key Hash，验证失败返回 null
     * @throws IOException 网络请求失败
     */
    @Override
    public String verifyAndGetKeyHash(String apiKey) throws IOException {
        JSONObject keyInfo = getCurrentKeyInfo(apiKey);
        if (keyInfo == null) {
            return null;
        }
        
        String label = keyInfo.getString("label");
        if (label == null || !label.startsWith("sk-or-")) {
            return null;
        }
        
        return extractKeyHashFromLabel(label);
    }

    /**
     * 获取所有 Key 列表
     * 
     * @return Key 列表 JSON
     * @throws IOException 网络请求失败
     */
    @Override
    public JSONObject listKeys() throws IOException {
        String url = openRouterConfig.getBaseUrl() + "/keys";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + openRouterConfig.getManagementKey())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("[OpenRouter] 获取Key列表失败: {}", response.code());
                return null;
            }
            String body = response.body().string();
            return JSON.parseObject(body);
        }
    }

    /**
     * 创建新的 API Key
     * 
     * @param name Key 名称
     * @param limit 额度限制（可为 null）
     * @return 创建结果 JSON
     * @throws IOException 网络请求失败
     */
    @Override
    public JSONObject createKey(String name, Double limit) throws IOException {
        String url = openRouterConfig.getBaseUrl() + "/keys";
        
        log.info("[OpenRouter] 创建Key请求: url={}, name={}, limit={}", url, name, limit);
        
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("name", name);
        if (limit != null) {
            bodyMap.put("limit", limit);
        }
        
        RequestBody body = RequestBody.create(
                JSON.toJSONString(bodyMap),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + openRouterConfig.getManagementKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            log.info("[OpenRouter] 创建Key响应: code={}, body={}", response.code(), respBody);
            
            if (!response.isSuccessful()) {
                log.error("[OpenRouter] 创建Key失败: code={}, body={}", response.code(), respBody);
                return null;
            }
            return JSON.parseObject(respBody);
        }
    }

    /**
     * 根据 Label 查找 Key Hash
     * 
     * @param label Key 标签（格式如 sk-or-xxx...yyy）
     * @return Key Hash
     * @throws IOException 网络请求失败
     */
    @Override
    public String findKeyHashByLabel(String label) throws IOException {
        if (label == null || !label.contains("...")) {
            log.error("[OpenRouter] label格式无效: {}", label);
            return null;
        }
        
        String prefix = label.substring(0, label.indexOf("..."));
        String suffix = label.substring(label.indexOf("...") + 3);
        
        log.info("[OpenRouter] 查找Key Hash: label={}, prefix={}, suffix={}", label, prefix, suffix);
        
        JSONObject keysResult = listKeys();
        if (keysResult == null) {
            log.error("[OpenRouter] 获取Key列表失败");
            return null;
        }
        
        com.alibaba.fastjson2.JSONArray dataArray = keysResult.getJSONArray("data");
        if (dataArray == null) {
            log.error("[OpenRouter] Key列表为空");
            return null;
        }
        
        for (int i = 0; i < dataArray.size(); i++) {
            com.alibaba.fastjson2.JSONObject keyInfo = dataArray.getJSONObject(i);
            String keyLabel = keyInfo.getString("label");
            String keyHash = keyInfo.getString("hash");
            
            if (keyLabel != null && keyLabel.startsWith(prefix) && keyLabel.endsWith(suffix)) {
                log.info("[OpenRouter] 找到匹配的Key: label={}, hash={}", keyLabel, keyHash);
                return keyHash;
            }
        }
        
        log.error("[OpenRouter] 未找到匹配的Key: label={}", label);
        return null;
    }

    /**
     * 从 Label 中提取 Key Hash
     */
    private String extractKeyHashFromLabel(String label) {
        if (label == null || !label.startsWith("sk-or-")) {
            return null;
        }
        return label.substring(6);
    }
}
