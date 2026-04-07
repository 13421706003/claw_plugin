package com.hsd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AbortRequest {
    @NotBlank(message = "userId不能为空")
    private String userId;

    @NotBlank(message = "messageId不能为空")
    private String messageId;

    private String clawId;
}
