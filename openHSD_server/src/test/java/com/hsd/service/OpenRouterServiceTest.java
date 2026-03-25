package com.hsd.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.config.OpenRouterConfig;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OpenRouterServiceTest {

    public static void main(String[] args) throws IOException {
        String managementKey = args.length > 0 ? args[0] : System.getenv("OPENROUTER_MANAGEMENT_KEY");
        
        if (managementKey == null || managementKey.isEmpty()) {
            System.out.println("Usage: java OpenRouterServiceTest <management_key>");
            System.out.println("Or set OPENROUTER_MANAGEMENT_KEY environment variable");
            return;
        }
        
        OkHttpClient httpClient = new OkHttpClient();
        String baseUrl = "https://openrouter.ai/api/v1";
        
        System.out.println("=== Test 1: List existing keys ===");
        listKeys(httpClient, baseUrl, managementKey);
        
        System.out.println("\n=== Test 2: Create a new key ===");
        JSONObject createResult = createKey(httpClient, baseUrl, managementKey, "Test Key from Java", 10.0);
        
        if (createResult != null) {
            String newKey = createResult.getString("key");
            JSONObject data = createResult.getJSONObject("data");
            String keyHash = data != null ? data.getString("hash") : null;
            
            System.out.println("New Key: " + newKey);
            System.out.println("Key Hash: " + keyHash);
            
            if (keyHash != null) {
                System.out.println("\n=== Test 3: Update key limit ===");
                updateKeyLimit(httpClient, baseUrl, managementKey, keyHash, 20.0);
                
                System.out.println("\n=== Test 4: Verify key works ===");
                if (newKey != null) {
                    verifyKey(httpClient, baseUrl, newKey);
                }
            }
        }
    }
    
    private static void listKeys(OkHttpClient httpClient, String baseUrl, String managementKey) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/keys")
                .addHeader("Authorization", "Bearer " + managementKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + body);
        }
    }
    
    private static JSONObject createKey(OkHttpClient httpClient, String baseUrl, String managementKey, String name, Double limit) throws IOException {
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
                .url(baseUrl + "/keys")
                .addHeader("Authorization", "Bearer " + managementKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + respBody);
            
            if (response.isSuccessful()) {
                return JSON.parseObject(respBody);
            }
            return null;
        }
    }
    
    private static void updateKeyLimit(OkHttpClient httpClient, String baseUrl, String managementKey, String keyHash, double newLimit) throws IOException {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("limit", newLimit);
        
        RequestBody body = RequestBody.create(
                JSON.toJSONString(bodyMap),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/keys/" + keyHash)
                .addHeader("Authorization", "Bearer " + managementKey)
                .addHeader("Content-Type", "application/json")
                .patch(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + respBody);
        }
    }
    
    private static void verifyKey(OkHttpClient httpClient, String baseUrl, String apiKey) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/key")
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + body);
        }
    }
}
