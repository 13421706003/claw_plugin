package com.hsd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 广播消息请求参数
 */
@Data
public class ClawBroadcastRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "消息ID不能为空")
    private String messageId;

    private String content = "";

    private List<AttachmentDTO> attachments;

    private Map<String, Object> context;

    private Integer timeout;
}
