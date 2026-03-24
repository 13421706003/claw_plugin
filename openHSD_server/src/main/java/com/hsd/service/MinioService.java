package com.hsd.service;

import com.hsd.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

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
     * 上传任意文件（Multipart）到 MinIO，返回 objectKey
     *
     * @param file      Spring MultipartFile
     * @param userId    用户 ID
     * @param clawId    设备 ID
     * @param messageId 消息 ID（预上传阶段可传 "upload"）
     * @param index     同一批次中第几个文件（0-based）
     * @return MinIO 对象路径（objectKey）
     */
    public String uploadFile(MultipartFile file, String userId, String clawId,
                             String messageId, int index) throws Exception {
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "file";
        String contentType  = file.getContentType() != null
                ? file.getContentType() : "application/octet-stream";

        // 提取扩展名：优先从原始文件名取，否则从 MIME 推断
        String ext = extensionFromFilename(originalName);
        if (ext.isEmpty()) {
            ext = extensionFromMime(contentType);
        }

        String objectKey = String.format("hsdclaw/%s/%s/%s/%d_%d.%s",
                userId, clawId, messageId, System.currentTimeMillis(), index, ext);

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .stream(is, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        }

        log.info("[MinIO] 文件上传成功：objectKey={}, size={}bytes, contentType={}",
                objectKey, file.getSize(), contentType);
        return objectKey;
    }

    /**
     * 根据 objectKey 拼接公开访问 URL
     * bucket 需在 MinIO 控制台设为 public read
     *
     * 格式：{externalUrl}/{bucketName}/{objectKey}
     * 例如：https://www.huashidai1.com/oss/aiworkbox-images/hsdclaw/2/claw_xxx/msg_xxx/123_0.jpg
     *
     * @param objectKey MinIO 对象路径
     * @return 公开访问 URL
     */
    public String presignUrl(String objectKey) {
        String url = minioProperties.getExternalUrl()
                + "/" + minioProperties.getBucketName()
                + "/" + objectKey;
        log.debug("[MinIO] 生成公开URL：{}", url);
        return url;
    }

    /**
     * 读取 MinIO 对象并转换为 DataURL，便于插件/模型直接消费图片内容。
     * 例：data:image/png;base64,xxxx
     */
    public String objectToDataUrl(String objectKey, String mimeType) throws Exception {
        String finalMime = (mimeType == null || mimeType.isBlank())
                ? "application/octet-stream" : mimeType;
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectKey)
                        .build());
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:" + finalMime + ";base64," + b64;
        }
    }

    /**
     * 根据 MIME 类型推断文件扩展名
     */
    private String extensionFromMime(String mimeType) {
        if (mimeType == null) return "bin";
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg"   -> "jpg";
            case "image/gif"                 -> "gif";
            case "image/webp"                -> "webp";
            case "image/bmp"                 -> "bmp";
            case "image/png"                 -> "png";
            case "application/pdf"           -> "pdf";
            case "application/msword"        -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel"  -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"       -> "xlsx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "text/plain"                -> "txt";
            case "text/markdown"             -> "md";
            case "text/csv"                  -> "csv";
            case "application/zip"           -> "zip";
            case "application/gzip"          -> "gz";
            case "application/json"          -> "json";
            default                          -> "bin";
        };
    }

    /**
     * 从文件名提取扩展名，例如 "report.pdf" → "pdf"
     */
    private String extensionFromFilename(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
