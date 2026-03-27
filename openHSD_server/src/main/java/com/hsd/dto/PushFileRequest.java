package com.hsd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文件推送请求参数
 */
@Data
public class PushFileRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "设备ID不能为空")
    private String clawId;

    @NotBlank(message = "文件URL不能为空")
    private String fileUrl;

    private String fileName = "file";

    private String fileType = "application/octet-stream";

    private Long fileSize;
}
