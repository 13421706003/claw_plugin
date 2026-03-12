package com.hsd.service;

import com.hsd.config.MinioProperties;
import com.hsd.dto.AttachmentDTO;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient      minioClient;
    private final MinioProperties  minioProperties;

    /**
     * 将 base64 图片上传到 MinIO，返回 objectKey
     *
     * @param base64    前端传来的 DataURL，例如 data:image/png;base64,iVBORw0K...
     * @param userId    用户 ID，用于构造存储路径
     * @param clawId    设备 ID，用于构造存储路径
     * @param messageId 消息 ID，用于构造存储路径
     * @param index     同一条消息中第几张图片（0-based）
     * @param name      原始文件名
     * @return MinIO 对象路径（objectKey），上传失败则抛出异常
     */
    public String uploadBase64Image(String base64, String userId, String clawId,
                                    String messageId, int index, String name) throws Exception {
        // 解析 DataURL: data:image/png;base64,xxxxx
        String mimeType = "image/png";
        String pureBase64 = base64;

        if (base64.startsWith("data:")) {
            int commaIdx = base64.indexOf(',');
            if (commaIdx > 0) {
                String header = base64.substring(0, commaIdx); // data:image/png;base64
                mimeType = header.substring(5, header.indexOf(';')); // image/png
                pureBase64 = base64.substring(commaIdx + 1);
            }
        }

        byte[] bytes = Base64.getDecoder().decode(pureBase64);

        // 生成文件扩展名
        String ext = extensionFromMime(mimeType);

        // 构造 objectKey: hsdclaw/{userId}/{clawId}/{messageId}/{timestamp}_{index}.{ext}
        String objectKey = String.format("hsdclaw/%s/%s/%s/%d_%d.%s",
                userId, clawId, messageId, System.currentTimeMillis(), index, ext);

        // 上传到 MinIO
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .stream(is, bytes.length, -1)
                            .contentType(mimeType)
                            .build()
            );
        }

        log.info("[MinIO] 图片上传成功：objectKey={}, size={}bytes", objectKey, bytes.length);
        return objectKey;
    }

    /**
     * 根据 objectKey 生成预签名访问 URL（外网地址）
     *
     * @param objectKey MinIO 对象路径
     * @return 外网可访问的预签名 URL
     */
    public String presignUrl(String objectKey) {
        try {
            String rawUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .expiry(minioProperties.getUrlExpiry(), TimeUnit.SECONDS)
                            .build()
            );

            // 将内网 endpoint 替换为外网 external-url
            // 例如: http://huashidai1.com:9000/aiworkbox-images/1/claw/... 
            //   →   https://www.huashidai1.com/oss/aiworkbox-images/1/claw/...
            String externalUrl = minioProperties.getExternalUrl();
            String endpoint    = minioProperties.getEndpoint();

            String publicUrl = rawUrl.replace(endpoint, externalUrl);

            log.debug("[MinIO] 生成预签名URL：objectKey={}", objectKey);
            return publicUrl;
        } catch (Exception e) {
            log.error("[MinIO] 生成预签名URL失败：objectKey={}, error={}", objectKey, e.getMessage());
            // 返回降级的静态路径，前端至少能看到路径
            return minioProperties.getExternalUrl() + "/" + minioProperties.getBucketName() + "/" + objectKey;
        }
    }

    /**
     * 根据 MIME 类型推断文件扩展名
     */
    private String extensionFromMime(String mimeType) {
        if (mimeType == null) return "png";
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif"               -> "gif";
            case "image/webp"              -> "webp";
            case "image/bmp"               -> "bmp";
            default                        -> "png";
        };
    }
}
