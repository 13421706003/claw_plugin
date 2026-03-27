package com.hsd.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建充值订单请求参数
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "充值金额不能为空")
    @Positive(message = "充值金额必须大于0")
    private BigDecimal amountUsd;

    private String paymentChannel = "wechat";

    private String paymentType = "NATIVE";
}
