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

    private String extractKeyHashFromLabel(String label) {
        if (label == null || !label.startsWith("sk-or-")) {
            return null;
        }
        return label.substring(6);
    }
}
