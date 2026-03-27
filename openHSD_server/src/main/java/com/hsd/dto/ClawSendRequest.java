package com.hsd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单设备发送消息请求参数
 */
@Data
public class ClawSendRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "消息ID不能为空")
    private String messageId;

    private String content = "";

    @NotBlank(message = "设备ID不能为空")
    private String clawId;

    private List<AttachmentDTO> attachments;

    private Map<String, Object> context;

    private Integer timeout;
}
