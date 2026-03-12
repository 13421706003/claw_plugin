package com.hsd.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();

        // 启动时确保 bucket 存在，不存在则自动创建
        try {
            String bucket = minioProperties.getBucketName();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] Bucket 不存在，已自动创建：{}", bucket);
            } else {
                log.info("[MinIO] Bucket 已存在：{}", bucket);
            }
        } catch (Exception e) {
            log.error("[MinIO] 初始化 Bucket 失败：{}", e.getMessage());
        }

        return client;
    }
}
