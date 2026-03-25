package com.hsd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenRouter API 配置类
 * 
 * 用于管理 OpenRouter 平台的 API 连接配置，包括 Management Key 和 API 基础地址。
 * 配置项从 application.yaml 中的 openrouter 前缀读取。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterConfig {
    
    /** OpenRouter Management API Key，用于管理用户 API Key 的额度 */
    private String managementKey;
    
    /** OpenRouter API 基础地址，默认为 https://openrouter.ai/api/v1 */
    private String baseUrl;
}
