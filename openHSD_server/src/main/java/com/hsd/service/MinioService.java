package com.hsd.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * MinIO 文件存储服务接口
 * 
 * 提供 MinIO 文件存储能力，包括：
 * - 文件上传（Base64 和 MultipartFile）
 * - 文件类型和大小验证
 * - 生成访问 URL
 */
public interface MinioService {
    
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
    String uploadBase64Image(String base64, String userId, String clawId,
                              String messageId, int index, String name) throws Exception;

    /**
     * 将文件上传到 MinIO
     * 
     * @param files 上传的文件列表
     * @param userId 用户ID
     * @param clawId 设备ID
     * @return 上传结果列表，每个结果包含 name, objectKey, url, size, type
     */
    List<Map<String, Object>> uploadFiles(String userId, String clawId, MultipartFile[] files);

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
    String uploadFile(MultipartFile file, String userId, String clawId,
                      String messageId, int index) throws Exception;

    /**
     * 获取文件扩展名
     * 
     * @param fileName 文件名
     * @return 扩展名（包含点号）
     */
    String getFileExtension(String fileName);
    
    /**
     * 验证文件
     * 
     * @param file 上传的文件
     * @return 验证结果，包含 valid, 和 reason 字段
     */
    Map<String, Object> validateFile(MultipartFile file);
    
    /**
     * 格式化文件大小
     * 
     * @param size 文件大小（字节）
     * @return 格式化后的字符串
     */
    String formatSize(long size);

    /**
     * 获取文件下载链接
     * 
     * @param objectKey 文件对象键
     * @return 下载链接
     */
    String presignUrl(String objectKey);

    /**
     * 根据 MIME 类型推断文件扩展名
     * 
     * @param mimeType MIME 类型
     * @return 扩展名（包含点号）
     */
    String extensionFromMime(String mimeType);

    /**
     * 从 MinIO 读取对象并转为 Data URL (base64)
     * 
     * @param objectKey MinIO 对象路径
     * @param mimeType MIME 类型
     * @return Data URL，格式 data:image/jpeg;base64,xxxx
     * @throws Exception 读取失败时抛出异常
     */
    String objectToDataUrl(String objectKey, String mimeType) throws Exception;
}
