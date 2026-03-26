package com.hsd.enums;

/**
 * 支付方式枚举
 * 
 * 定义支持的支付方式类型，用于区分不同的支付场景。
 */
public enum PayType {
    
    /** 扫码支付（生成二维码供用户扫描） */
    NATIVE,
    
    /** H5支付（手机网页支付） */
    H5,
    
    /** APP支付（原生APP内支付） */
    APP,
    
    /** JSAPI支付（公众号/小程序支付） */
    JSAPI
}
