package com.hsd.enums;

import lombok.Getter;

/**
 * 支付渠道枚举
 * 
 * 定义支持的支付渠道，用于多渠道支付的路由和标识。
 */
@Getter
public enum PaymentChannel {
    
    /** 微信支付 */
    WECHAT("wechat"),
    
    /** 支付宝支付 */
    ALIPAY("ali");

    /** 渠道标识码 */
    private final String code;

    PaymentChannel(String code) {
        this.code = code;
    }

    /**
     * 根据标识码获取支付渠道枚举
     * 
     * @param code 渠道标识码（不区分大小写）
     * @return 对应的支付渠道枚举，未匹配时默认返回 WECHAT
     */
    public static PaymentChannel fromCode(String code) {
        for (PaymentChannel channel : values()) {
            if (channel.getCode().equalsIgnoreCase(code)) {
                return channel;
            }
        }
        return WECHAT;
    }
}
