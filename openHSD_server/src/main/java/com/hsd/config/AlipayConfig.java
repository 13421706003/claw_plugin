package com.hsd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝支付配置类
 * 
 * 管理支付宝支付相关的配置信息，配置项从 application.yaml 中的 alipay 前缀读取。
 * 当前为预留配置，真实支付功能待后续开发。
 * 
 * 配置示例：
 * <pre>
 * alipay:
 *   app-id: 你的应用ID
 *   private-key: 应用私钥
 *   alipay-public-key: 支付宝公钥
 *   notify-url: 支付结果回调地址
 *   return-url: 支付完成跳转地址
 *   mock: true  # 开发测试时启用模拟模式
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {
    
    /** 支付宝应用ID */
    private String appId;
    
    /** 应用私钥（用于签名请求） */
    private String privateKey;
    
    /** 支付宝公钥（用于验签回调） */
    private String alipayPublicKey;
    
    /** 支付结果异步通知地址，需公网可访问 */
    private String notifyUrl;
    
    /** 支付完成同步跳转地址 */
    private String returnUrl;
    
    /** 支付宝网关地址，默认为正式环境 */
    private String serverUrl = "https://openapi.alipay.com/gateway.do";
    
    /** 是否启用模拟模式（开发测试用，生产环境必须为 false） */
    private Boolean mock = true;
}
