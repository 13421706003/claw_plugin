package com.hsd.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单实体类
 * 
 * 表示用户充值订单的完整信息，包括订单基本信息、支付金额、
 * 汇率、支付状态以及微信支付相关信息。
 */
@Data
public class RechargeOrder {
    
    /** 订单主键ID */
    private Long id;
    
    /** 商户订单号，唯一标识 */
    private String orderNo;
    
    /** 用户ID，关联 users 表 */
    private Long userId;
    
    /** 充值金额（人民币，单位：元） */
    private BigDecimal amountCny;
    
    /** 充值金额（美元，单位：USD） */
    private BigDecimal amountUsd;
    
    /** 兑换汇率（默认 8.0） */
    private BigDecimal exchangeRate;
    
    /** 订单状态：0-待支付 1-已支付 2-已分配额度 3-已关闭 */
    private Integer status;
    
    /** 支付渠道：wechat/alipay */
    private String paymentChannel;
    
    /** 支付方式：NATIVE/H5/APP/JSAPI */
    private String paymentType;
    
    /** 渠道订单号（支付成功后由支付渠道返回） */
    private String channelOrderId;
    
    /** 微信支付订单号（支付成功后由微信返回，已废弃，使用channelOrderId） */
    @Deprecated
    private String wechatOrderId;
    
    /** 支付二维码URL（微信 Native 支付链接） */
    private String qrcodeUrl;
    
    /** 支付完成时间 */
    private LocalDateTime paidAt;
    
    /** 订单创建时间 */
    private LocalDateTime createdAt;
    
    /** 订单最后更新时间 */
    private LocalDateTime updatedAt;

    /** 订单状态常量：待支付 */
    public static final int STATUS_PENDING = 0;
    
    /** 订单状态常量：已支付（等待分配额度） */
    public static final int STATUS_PAID = 1;
    
    /** 订单状态常量：已分配额度（充值完成） */
    public static final int STATUS_ALLOCATED = 2;
    
    /** 订单状态常量：已关闭（超时或取消） */
    public static final int STATUS_CLOSED = 3;
}
