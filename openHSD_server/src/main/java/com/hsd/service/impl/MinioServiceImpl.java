package com.hsd.service.impl;

import com.hsd.config.MinioProperties;
import com.hsd.service.MinioService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * MinIO 文件存储服务实现类
 * 
 * 实现 MinIO 文件存储的具体逻辑，
 * 使用 MinIO Java SDK 进行文件操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /** 允许上传的文件类型 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "image/bmp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/markdown", "text/csv",
            "application/zip", "application/gzip",
            "application/json"
    );

    /** 最大文件大小（50MB） */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    /**
     * 将 base64 图片上传到 MinIO
     * 
     * @param base64 前端传来的 DataURL，例如 data:image/png;base64,iVBORw0K...
     * @param userId 用户 ID，用于构造存储路径
     * @param clawId 设备 ID，用于构造存储路径
     * @param messageId 消息 ID，用于构造存储路径
     * @param index 同一条消息中第几张图片（0-based）
     * @param name 原始文件名
     * @return MinIO 对象路径（objectKey）
     * @throws Exception 上传失败时抛出异常
     */
    @Override
    public String uploadBase64Image(String base64, String userId, String clawId,
                                    String messageId, int index, String name) throws Exception {
        String mimeType = "image/png";
        String pureBase64 = base64;

        if (base64.startsWith("data:")) {
            int commaIdx = base64.indexOf(',');
            if (commaIdx > 0) {
                String header = base64.substring(0, commaIdx);
                mimeType = header.substring(5, header.indexOf(';'));
                pureBase64 = base64.substring(commaIdx + 1);
            }
        }

        byte[] bytes = Base64.getDecoder().decode(pureBase64);

        String ext = extensionFromMime(mimeType);

        String objectKey = String.format("hsdclaw/%s/%s/%s/%d_%d.%s",
                userId, clawId, messageId, System.currentTimeMillis(), index, ext);

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
     * 将文件上传到 MinIO
     * 
     * @param files 上传的文件列表
     * @param userId 用户ID
     * @param clawId 设备ID
     * @return 上传结果列表，每个结果包含 name, objectKey, url, size, type
     */
    @Override
    public List<Map<String, Object>> uploadFiles(String userId, String clawId, MultipartFile[] files) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            Map<String, Object> result = new HashMap<>();
            
            try {
                Map<String, Object> validation = validateFile(file);
                if (!(Boolean) validation.get("valid")) {
                    result.put("error", validation.get("reason"));
                    results.add(result);
                    continue;
                }

                String objectKey = uploadFile(file, userId, clawId, "upload", i);
                
                result.put("name", file.getOriginalFilename());
                result.put("objectKey", objectKey);
                result.put("url", presignUrl(objectKey));
                result.put("size", file.getSize());
                result.put("type", file.getContentType());
                
                log.info("[MinIO] 文件上传成功: {}", objectKey);
            } catch (Exception e) {
                log.error("[MinIO] 文件上传失败: {}", e.getMessage());
                result.put("error", e.getMessage());
            }
            
            results.add(result);
        }
        
        return results;
    }

    /**
     * 上传任意文件（Multipart）到 MinIO
     * 
     * @param file Spring MultipartFile
     * @param userId 用户 ID
     * @param clawId 设备 ID
     * @param messageId 消息 ID（预上传阶段可传 "upload"）
     * @param index 同一批次中第几个文件（0-based）
     * @return MinIO 对象路径（objectKey）
     * @throws Exception 上传失败时抛出异常
     */
    @Override
    public String uploadFile(MultipartFile file, String userId, String clawId,
                             String messageId, int index) throws Exception {
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "file";
        String contentType = file.getContentType() != null
                ? file.getContentType() : "application/octet-stream";

        String ext = getFileExtension(originalName);
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
     * 获取文件扩展名
     * 
     * @param fileName 文件名
     * @return 扩展名（不包含点号）
     */
    @Override
    public String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 验证文件
     * 
     * @param file 上传的文件
     * @return 验证结果，包含 valid 和 reason 字段
     */
    @Override
    public Map<String, Object> validateFile(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        if (file == null || file.isEmpty()) {
            result.put("valid", false);
            result.put("reason", "文件为空");
            return result;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            result.put("valid", false);
            result.put("reason", "文件大小超过50MB限制");
            return result;
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            result.put("valid", false);
            result.put("reason", "不支持的文件类型: " + contentType);
            return result;
        }

        result.put("valid", true);
        return result;
    }

    /**
     * 格式化文件大小
     * 
     * @param size 文件大小（字节）
     * @return 格式化后的字符串
     */
    @Override
    public String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 根据 objectKey 拼接公开访问 URL
     * 
     * 格式：{externalUrl}/{bucketName}/{objectKey}
     * 例如：https://www.huashidai1.com/oss/aiworkbox-images/hsdclaw/2/claw_xxx/msg_xxx/123_0.jpg
     * 
     * @param objectKey MinIO 对象路径
     * @return 公开访问 URL
     */
    @Override
    public String presignUrl(String objectKey) {
        String url = minioProperties.getExternalUrl()
                + "/" + minioProperties.getBucketName()
                + "/" + objectKey;
        log.debug("[MinIO] 生成公开URL：{}", url);
        return url;
    }

    /**
     * 根据 MIME 类型推断文件扩展名
     * 
     * @param mimeType MIME 类型
     * @return 扩展名（不包含点号）
     */
    @Override
    public String extensionFromMime(String mimeType) {
        if (mimeType == null) return "bin";
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "image/png" -> "png";
            case "application/pdf" -> "pdf";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "text/plain" -> "txt";
            case "text/markdown" -> "md";
            case "text/csv" -> "csv";
            case "application/zip" -> "zip";
            case "application/gzip" -> "gz";
            case "application/json" -> "json";
            default -> "bin";
        };
    }

    @Override
    public String objectToDataUrl(String objectKey, String mimeType) throws Exception {
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectKey)
                        .build())) {
            byte[] bytes = is.readAllBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return "data:" + mimeType + ";base64," + base64;
        }
    }
}
