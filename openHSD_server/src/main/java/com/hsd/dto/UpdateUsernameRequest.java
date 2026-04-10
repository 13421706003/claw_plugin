package com.hsd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改用户名请求参数
 */
@Data
public class UpdateUsernameRequest {

    @NotBlank(message = "新用户名不能为空")
    private String newUsername;
}
