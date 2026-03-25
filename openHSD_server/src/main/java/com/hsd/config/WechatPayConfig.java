package com.hsd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付配置类
 * 
 * 用于管理微信支付相关的配置信息，包括商户信息、API 密钥和回调地址。
 * 配置项从 application.yaml 中的 wechat.pay 前缀读取。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.pay")
public class WechatPayConfig {
    
    /** 微信公众号/小程序 AppID */
    private String appId;
    
    /** 微信支付商户号 */
    private String mchId;
    
    /** 微信支付 API V2 密钥（部分旧接口使用） */
    private String apiKey;
    
    /** 微信支付 API V3 密钥（用于签名和解密回调数据） */
    private String apiV3Key;
    
    /** 支付结果回调通知地址，需要公网可访问 */
    private String notifyUrl;
    
    /** 商户证书文件路径（用于签名请求） */
    private String certPath;
    
    /** 是否启用模拟模式（开发测试用，生产环境必须为 false） */
    private Boolean mock = false;
}
