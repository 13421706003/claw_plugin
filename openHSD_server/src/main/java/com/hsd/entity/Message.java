package com.hsd.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Message {
    private Long id;
    private String messageId;
    private Long userId;
    private String clawId;
    private String role;
    private String content;
    /**
     * 附件 JSON 数组，存储 objectKey（MinIO 路径），不存 URL
     * 格式：[{"objectKey":"1/claw/msg/123_0.png","name":"image.png","type":"image/png"}]
     * 无附件时为 null
     */
    private String attachments;
    private String status;
    private LocalDateTime createdAt;
}
