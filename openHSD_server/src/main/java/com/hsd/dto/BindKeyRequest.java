package com.hsd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 绑定 API Key 请求参数
 */
@Data
public class BindKeyRequest {

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;
}
