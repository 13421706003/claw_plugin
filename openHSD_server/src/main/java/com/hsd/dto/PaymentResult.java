package com.hsd.dto;

import lombok.Data;

/**
 * 支付结果DTO
 * 
 * 统一的支付回调结果数据结构，用于解耦不同支付渠道的回调数据格式差异。
 * 各支付渠道的回调处理实现需将原始回调数据转换为此统一格式。
 */
@Data
public class PaymentResult {
    
    /** 支付是否成功 */
    private boolean success;
    
    /** 商户订单号 */
    private String orderNo;
    
    /** 支付渠道订单号（微信/支付宝的订单号） */
    private String channelOrderId;
    
    /** 支付完成时间 */
    private String paidAt;
    
    /** 支付金额（单位：分） */
    private Integer amountCents;
    
    /** 事件类型（如 TRANSACTION.SUCCESS、TRADE_SUCCESS、TRANSACTION.CLOSED） */
    private String eventType;
    
    /** 是否为订单关闭事件 */
    private boolean closed;
}
