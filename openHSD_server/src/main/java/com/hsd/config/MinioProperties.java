package com.hsd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 内网连接地址，例如 http://huashidai1.com:9000 */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    /** 存储桶名称 */
    private String bucketName;

    /** 预签名 URL 有效期（秒），默认 604800（7天） */
    private int urlExpiry = 604800;

    /** 对外访问地址（Nginx 反代后的公网地址），例如 https://www.huashidai1.com/oss */
    private String externalUrl;

    /**
     * 预签名路径前缀，例如 /oss
     * MinIO SDK 生成的预签名 URL 基于 endpoint，需要把 endpoint 替换为 externalUrl
     */
    private String presignBasePath;
}
