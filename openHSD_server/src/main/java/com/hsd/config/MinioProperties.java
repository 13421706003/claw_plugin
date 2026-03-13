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

    /** 对外访问地址（Nginx 反代后的公网地址），例如 https://www.huashidai1.com/oss */
    private String externalUrl;
}
