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
    private String status;
    private LocalDateTime createdAt;
}
