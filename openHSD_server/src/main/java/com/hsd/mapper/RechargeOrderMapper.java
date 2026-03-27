package com.hsd.mapper;

import com.hsd.entity.RechargeOrder;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 充值订单数据访问层接口
 * 
 * 提供充值订单表的 CRUD 操作，支持订单创建、查询和状态更新。
 */
@Mapper
public interface RechargeOrderMapper {

    /**
     * 插入新的充值订单记录
     * 
     * @param order 充值订单实体
     * @return 影响行数，同时会回填自增主键到 order.id
     */
    @Insert("INSERT INTO recharge_order (order_no, user_id, amount_cny, amount_usd, exchange_rate, status, qrcode_url, payment_channel, payment_type, created_at) " +
            "VALUES (#{orderNo}, #{userId}, #{amountCny}, #{amountUsd}, #{exchangeRate}, #{status}, #{qrcodeUrl}, #{paymentChannel}, #{paymentType}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RechargeOrder order);

    /**
     * 根据订单号查询订单信息
     * 
     * @param orderNo 商户订单号
     * @return 订单实体，不存在则返回 null
     */
    @Select("SELECT * FROM recharge_order WHERE order_no = #{orderNo} LIMIT 1")
    RechargeOrder findByOrderNo(String orderNo);

    /**
     * 根据订单ID查询订单信息
     * 
     * @param id 订单主键ID
     * @return 订单实体，不存在则返回 null
     */
    @Select("SELECT * FROM recharge_order WHERE id = #{id} LIMIT 1")
    RechargeOrder findById(Long id);

    /**
     * 查询用户的充值订单历史记录
     * 
     * @param userId 用户ID
     * @param limit 返回记录数量限制
     * @return 订单列表，按创建时间倒序排列
     */
    @Select("SELECT * FROM recharge_order WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<RechargeOrder> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 更新订单支付状态（支付成功后调用）
     * 
     * @param orderNo 商户订单号
     * @param status 新状态值
     * @param wechatOrderId 微信支付订单号
     * @param paidAt 支付完成时间
     * @return 影响行数
     */
    @Update("UPDATE recharge_order SET status = #{status}, channel_order_id = #{channelOrderId}, paid_at = #{paidAt}, updated_at = NOW() WHERE order_no = #{orderNo}")
    int updatePayStatus(@Param("orderNo") String orderNo, @Param("status") Integer status, 
                        @Param("channelOrderId") String channelOrderId, @Param("paidAt") String paidAt);

    /**
     * 更新订单状态（通用方法）
     * 
     * @param orderNo 商户订单号
     * @param status 新状态值
     * @return 影响行数
     */
    @Update("UPDATE recharge_order SET status = #{status}, updated_at = NOW() WHERE order_no = #{orderNo}")
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") Integer status);

    /**
     * 根据订单号悲观锁查询（用于事务中防止并发）
     * 
     * @param orderNo 商户订单号
     * @return 订单实体，不存在则返回 null
     */
    @Select("SELECT * FROM recharge_order WHERE order_no = #{orderNo} FOR UPDATE")
    RechargeOrder findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    /**
     * 查询过期的微信待支付订单（用于定时任务）
     * 
     * 查询微信渠道、待支付状态、创建时间早于指定时间的订单。
     * 
     * @param expireThreshold 过期阈值（创建时间早于此时间）
     * @param limit 返回数量限制
     * @return 过期订单列表
     */
    @Select("SELECT * FROM recharge_order WHERE status = 0 AND payment_channel = 'wechat' AND created_at < #{expireThreshold} ORDER BY created_at ASC LIMIT #{limit}")
    List<RechargeOrder> findExpiredWechatOrders(@Param("expireThreshold") LocalDateTime expireThreshold, @Param("limit") int limit);
}
