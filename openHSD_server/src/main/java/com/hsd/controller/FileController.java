package com.hsd.controller;

import com.hsd.dto.AttachmentDTO;
import com.hsd.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传接口
 * POST /api/file/upload — 上传文件到 MinIO，返回 objectKey 和公开访问 URL
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequiredArgsConstructor
public class FileController {

    private final MinioService minioService;

    /** 单文件最大 50MB */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 允许的 MIME 类型前缀 */
    private static final List<String> ALLOWED_MIME_PREFIXES = List.of(
            "image/",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/zip",
            "application/gzip",
            "application/json"
    );

    /**
     * POST /api/file/upload
     *
     * 参数（multipart/form-data）：
     *   - files   : 文件列表（支持多文件）
     *   - userId  : 用户 ID
     *   - clawId  : 设备 ID（用于构造 MinIO 路径）
     *
     * 返回：
     * {
     *   "success": true,
     *   "files": [
     *     { "objectKey": "...", "url": "...", "name": "...", "type": "...", "size": 12345 }
     *   ]
     * }
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("userId")  String userId,
            @RequestParam("clawId")  String clawId) {

        Map<String, Object> result = new HashMap<>();

        if (files == null || files.isEmpty()) {
            result.put("success", false);
            result.put("message", "未选择任何文件");
            return ResponseEntity.badRequest().body(result);
        }

        // 使用一个固定的 bucket 前缀标识预上传
        String messageId = "upload";

        List<AttachmentDTO> uploaded = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            // 文件大小校验
            if (file.getSize() > MAX_FILE_SIZE) {
                result.put("success", false);
                result.put("message", "文件 [" + file.getOriginalFilename() + "] 超过 50MB 限制");
                return ResponseEntity.badRequest().body(result);
            }

            // MIME 类型校验
            String contentType = file.getContentType() != null ? file.getContentType() : "";
            if (!isAllowedMime(contentType)) {
                result.put("success", false);
                result.put("message", "不支持的文件类型：" + contentType);
                return ResponseEntity.badRequest().body(result);
            }

            try {
                String objectKey = minioService.uploadFile(file, userId, clawId, messageId, i);
                String url       = minioService.presignUrl(objectKey);
                String name      = file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "file";

                uploaded.add(new AttachmentDTO(objectKey, url, name, contentType, file.getSize()));
                log.info("[FileController] 上传成功：userId={}, clawId={}, name={}, size={}",
                        userId, clawId, name, file.getSize());
            } catch (Exception e) {
                log.error("[FileController] 上传失败：userId={}, clawId={}, error={}", userId, clawId, e.getMessage());
                result.put("success", false);
                result.put("message", "文件上传失败：" + e.getMessage());
                return ResponseEntity.internalServerError().body(result);
            }
        }

        result.put("success", true);
        result.put("files", uploaded);
        return ResponseEntity.ok(result);
    }

    private boolean isAllowedMime(String mime) {
        if (mime == null || mime.isBlank()) return false;
        String lower = mime.toLowerCase();
        return ALLOWED_MIME_PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
