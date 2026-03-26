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

    /** 兼容旧配置：merchant_private_key */
    private String merchantPrivateKey;
    
    /** 支付宝公钥（用于验签回调） */
    private String alipayPublicKey;

    /** 可选：商家支付宝用户ID（seller_id） */
    private String sellerId;
    
    /** 支付结果异步通知地址，需公网可访问 */
    private String notifyUrl;
    
    /** 支付完成同步跳转地址 */
    private String returnUrl;
    
    /** 支付宝网关地址，默认为正式环境 */
    private String serverUrl = "https://openapi.alipay.com/gateway.do";

    /** 支付宝沙箱网关地址（application-dev.yaml: gatewayUrl） */
    private String gatewayUrl;

    /** 签名方式（application-dev.yaml: sign_type） */
    private String signType = "RSA2";

    /** 字符编码（application-dev.yaml: charset） */
    private String charset = "utf-8";

    /** 订单超时时间（application-dev.yaml: timeout） */
    private String timeout = "10m";
    
    /** 是否启用模拟模式（开发测试用，生产环境必须为 false） */
    private Boolean mock = true;

    /**
     * 兼容 application-dev.yaml 中的 gatewayUrl 配置；
     * 如果配置了 gatewayUrl，则优先使用沙箱网关。
     */
    public String getServerUrl() {
        if (gatewayUrl != null && !gatewayUrl.isEmpty()) {
            return gatewayUrl;
        }
        return serverUrl;
    }

    /**
     * 兼容两种私钥配置名：
     * 1) alipay.private-key / private_key
     * 2) alipay.merchant-private-key / merchant_private_key
     */
    public String getPrivateKey() {
        if (privateKey != null && !privateKey.isBlank()) {
            return privateKey;
        }
        return merchantPrivateKey;
    }
}
