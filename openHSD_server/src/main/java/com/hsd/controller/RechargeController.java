package com.hsd.controller;

import com.alibaba.fastjson2.JSONObject;
import com.hsd.dto.BindKeyRequest;
import com.hsd.dto.CreateOrderRequest;
import com.hsd.service.OpenRouterService;
import com.hsd.service.PaymentService;
import com.hsd.service.RechargeService;
import com.hsd.dto.PaymentResult;
import com.hsd.enums.PayType;
import com.hsd.enums.PaymentChannel;
import com.hsd.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 充值控制器
 * 
 * 提供充值相关的 RESTful API，包括：
 * - API Key 绑定与查询
 * - 充值订单创建与状态查询
 * - 微信/支付宝支付回调处理
 * - 模拟支付（测试环境）
 */
@Slf4j
@RestController
@RequestMapping("/api/recharge")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RechargeController {

    private final RechargeService rechargeService;
    private final Map<String, PaymentService> paymentServices;
    private final JwtUtil jwtUtil;
    private final OpenRouterService openRouterService;

    /**
     * 获取用户绑定的 API Key 信息
     * 
     * 查询当前用户是否已绑定 OpenRouter API Key，
     * 如果已绑定则返回 Key 的限额、用量等信息。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @return Key 信息响应
     */
    @GetMapping("/key-info")
    public ResponseEntity<Map<String, Object>> getKeyInfo(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Map<String, Object> result = rechargeService.getKeyInfo(userId);
        return ResponseEntity.ok(result);
    }

    /**
     * 绑定 OpenRouter API Key
     * 
     * 用户绑定自己的 OpenRouter API Key，
     * 绑定后才能进行充值操作。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param request 绑定请求参数
     * @return 绑定结果响应
     */
    @PostMapping("/bind-key")
    public ResponseEntity<Map<String, Object>> bindKey(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody BindKeyRequest request) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        String apiKey = request.getApiKey();
        Map<String, Object> result = rechargeService.bindKey(userId, apiKey.trim());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前汇率
     * 
     * 返回美元兑人民币的充值汇率，
     * 前端用于实时计算充值金额。
     * 
     * @return 汇率信息响应
     */
    @GetMapping("/exchange-rate")
    public ResponseEntity<Map<String, Object>> getExchangeRate() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("rate", rechargeService.getExchangeRate());
        return ResponseEntity.ok(result);
    }

    /**
     * 创建充值订单
     * 
     * 用户发起充值请求，系统创建支付订单并返回支付二维码。
     * 支持多种支付渠道（微信、支付宝）和支付方式（Native、H5等）。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param request 创建订单请求参数
     * @return 订单信息，包含订单号、支付二维码等
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateOrderRequest request) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        PaymentChannel channel = PaymentChannel.fromCode(request.getPaymentChannel());
        PayType payType;
        try {
            payType = PayType.valueOf(request.getPaymentType().toUpperCase());
        } catch (IllegalArgumentException e) {
            payType = PayType.NATIVE;
        }

        Map<String, Object> result = rechargeService.createOrder(userId, request.getAmountUsd(), channel, payType);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询订单状态
     * 
     * 根据订单号查询订单的当前状态，
     * 前端用于轮询支付结果。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param orderNo 商户订单号
     * @return 订单状态信息
     */
    @GetMapping("/status/{orderNo}")
    public ResponseEntity<Map<String, Object>> getOrderStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String orderNo) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Map<String, Object> result = rechargeService.getOrderStatus(orderNo);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取充值历史记录
     * 
     * 查询当前用户的充值订单列表，
     * 按时间倒序排列。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param limit 返回记录数量，默认 10 条，最多 50 条
     * @return 充值历史记录列表
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getOrderHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "10") int limit) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Map<String, Object> result = rechargeService.getOrderHistory(userId, Math.min(limit, 50));
        return ResponseEntity.ok(result);
    }

    /**
     * 微信支付回调
     * 
     * 接收微信支付异步通知，验证签名后更新订单状态并分配额度。
     * 
     * @param request HTTP 请求
     * @return 处理结果响应
     */
    @PostMapping("/wechat/notify")
    public ResponseEntity<Map<String, String>> wechatNotify(HttpServletRequest request) {
        log.info("[RechargeController] 收到微信支付通知");
        
        PaymentService wechatPayService = paymentServices.get(PaymentChannel.WECHAT.getServiceBeanName());
        
        try {
            if (!wechatPayService.verifyNotify(request)) {
                log.error("[RechargeController] 微信支付通知验签失败");
                Map<String, String> result = new HashMap<>();
                result.put("code", "FAIL");
                result.put("message", "验签失败");
                return ResponseEntity.status(401).body(result);
            }

            PaymentResult notifyData = wechatPayService.parseNotify(request);
            if (notifyData == null) {
                log.error("[RechargeController] 解析通知数据失败");
                Map<String, String> result = new HashMap<>();
                result.put("code", "FAIL");
                result.put("message", "解析失败");
                return ResponseEntity.badRequest().body(result);
            }

            if (!"TRANSACTION.SUCCESS".equals(notifyData.getEventType())) {
                log.info("[RechargeController] 非成功事件: {}", notifyData.getEventType());
                return ResponseEntity.ok(wechatPayService.buildSuccessResponse());
            }

            String orderNo = notifyData.getOrderNo();
            String channelOrderId = notifyData.getChannelOrderId();
            String paidAt = notifyData.getPaidAt();

            log.info("[RechargeController] 支付成功: orderNo={}, channelOrderId={}", orderNo, channelOrderId);
            
            boolean allocated = rechargeService.handlePaySuccess(orderNo, channelOrderId, paidAt);
            if (!allocated) {
                log.error("[RechargeController] 额度分配失败，需人工处理: orderNo={}", orderNo);
            }

            return ResponseEntity.ok(wechatPayService.buildSuccessResponse());
            
        } catch (Exception e) {
            log.error("[RechargeController] 处理微信支付通知失败", e);
            Map<String, String> result = new HashMap<>();
            result.put("code", "FAIL");
            result.put("message", "处理失败");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 支付宝支付回调
     * 
     * 接收支付宝异步通知，验证签名后更新订单状态并分配额度。
     * 
     * @param request HTTP 请求
     * @return 处理结果响应
     */
    @PostMapping("/alipay/notify")
    public ResponseEntity<Map<String, String>> alipayNotify(HttpServletRequest request) {
        log.info("[RechargeController] 收到支付宝支付通知");
        
        PaymentService alipayService = paymentServices.get(PaymentChannel.ALIPAY.getServiceBeanName());
        
        try {
            if (!alipayService.verifyNotify(request)) {
                log.error("[RechargeController] 支付宝支付通知验签失败");
                Map<String, String> result = new HashMap<>();
                result.put("code", "FAIL");
                result.put("message", "验签失败");
                return ResponseEntity.status(401).body(result);
            }

            PaymentResult notifyData = alipayService.parseNotify(request);
            if (notifyData == null) {
                log.error("[RechargeController] 解析通知数据失败");
                Map<String, String> result = new HashMap<>();
                result.put("code", "FAIL");
                result.put("message", "解析失败");
                return ResponseEntity.badRequest().body(result);
            }

            String orderNo = notifyData.getOrderNo();
            String channelOrderId = notifyData.getChannelOrderId();
            String paidAt = notifyData.getPaidAt();

            log.info("[RechargeController] 支付宝支付成功: orderNo={}, channelOrderId={}", orderNo, channelOrderId);
            
            boolean allocated = rechargeService.handlePaySuccess(orderNo, channelOrderId, paidAt);
            if (!allocated) {
                log.error("[RechargeController] 额度分配失败，需人工处理: orderNo={}", orderNo);
            }

            return ResponseEntity.ok(alipayService.buildSuccessResponse());
            
        } catch (Exception e) {
            log.error("[RechargeController] 处理支付宝支付通知失败", e);
            Map<String, String> result = new HashMap<>();
            result.put("code", "FAIL");
            result.put("message", "处理失败");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 模拟支付成功
     * 
     * 仅在模拟模式下可用，用于测试环境模拟支付成功流程。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param orderNo 订单号
     * @return 模拟支付结果
     */
    @PostMapping("/mock/pay/{orderNo}")
    public ResponseEntity<Map<String, Object>> mockPaySuccess(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String orderNo) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        PaymentService wechatPayService = paymentServices.get(PaymentChannel.WECHAT.getServiceBeanName());
        if (!wechatPayService.isMockMode()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "非模拟模式，禁止访问");
            return ResponseEntity.status(403).body(result);
        }

        log.info("[RechargeController] 模拟支付: orderNo={}, userId={}", orderNo, userId);
        Map<String, Object> result = rechargeService.mockPaySuccess(orderNo);
        return ResponseEntity.ok(result);
    }

    /**
     * 从 Authorization 头中提取用户ID
     * 
     * 解析 Bearer Token 并从中提取用户ID，
     * 用于验证用户身份。
     * 
     * @param authHeader Authorization 请求头
     * @return 用户ID，Token 无效返回 null
     */
    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        try {
            return jwtUtil.getUserId(token);
        } catch (Exception e) {
            log.error("[RechargeController] 解析token失败", e);
            return null;
        }
    }

    /**
     * 测试接口：创建 OpenRouter Key
     * 
     * 仅在模拟模式下可用，用于测试创建 OpenRouter 子 Key。
     * 
     * @param name Key 名称
     * @param limit 额度限制（可选）
     * @return 创建结果
     */
    @PostMapping("/test/create-key")
    public ResponseEntity<Map<String, Object>> testCreateKey(
            @RequestParam String name,
            @RequestParam(required = false) Double limit) {
        
        Map<String, Object> result = new HashMap<>();
        
        PaymentService wechatPayService = paymentServices.get(PaymentChannel.WECHAT.getServiceBeanName());
        if (!wechatPayService.isMockMode()) {
            result.put("success", false);
            result.put("message", "非模拟模式，禁止访问");
            return ResponseEntity.status(403).body(result);
        }
        
        try {
            log.info("[RechargeController] 测试创建Key: name={}, limit={}", name, limit);
            JSONObject createResult = openRouterService.createKey(name, limit);
            
            if (createResult != null) {
                result.put("success", true);
                result.put("data", createResult);
            } else {
                result.put("success", false);
                result.put("message", "创建Key失败");
            }
        } catch (Exception e) {
            log.error("[RechargeController] 测试创建Key失败", e);
            result.put("success", false);
            result.put("message", "创建Key异常: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 测试接口：列出所有 OpenRouter Key
     * 
     * 仅在模拟模式下可用，用于测试查看所有 OpenRouter 子 Key。
     * 
     * @return Key 列表
     */
    @GetMapping("/test/list-keys")
    public ResponseEntity<Map<String, Object>> testListKeys() {
        
        Map<String, Object> result = new HashMap<>();
        
        PaymentService wechatPayService = paymentServices.get(PaymentChannel.WECHAT.getServiceBeanName());
        if (!wechatPayService.isMockMode()) {
            result.put("success", false);
            result.put("message", "非模拟模式，禁止访问");
            return ResponseEntity.status(403).body(result);
        }
        
        try {
            log.info("[RechargeController] 测试列出Keys");
            JSONObject listResult = openRouterService.listKeys();
            
            if (listResult != null) {
                result.put("success", true);
                result.put("data", listResult);
            } else {
                result.put("success", false);
                result.put("message", "获取Key列表失败");
            }
        } catch (Exception e) {
            log.error("[RechargeController] 测试列出Keys失败", e);
            result.put("success", false);
            result.put("message", "获取Key列表异常: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 测试接口：更新 OpenRouter Key 额度
     * 
     * 仅在模拟模式下可用，用于测试直接更新 OpenRouter Key 的额度限制。
     * 
     * @param keyHash Key 的哈希值
     * @param limit 新的额度限制
     * @return 更新结果
     */
    @PatchMapping("/test/update-limit")
    public ResponseEntity<Map<String, Object>> testUpdateLimit(
            @RequestParam String keyHash,
            @RequestParam Double limit) {
        
        Map<String, Object> result = new HashMap<>();
        
        PaymentService wechatPayService = paymentServices.get(PaymentChannel.WECHAT.getServiceBeanName());
        if (!wechatPayService.isMockMode()) {
            result.put("success", false);
            result.put("message", "非模拟模式，禁止访问");
            return ResponseEntity.status(403).body(result);
        }
        
        try {
            log.info("[RechargeController] 测试更新额度: keyHash={}, limit={}", keyHash, limit);
            
            boolean success = openRouterService.updateKeyLimit(keyHash, limit);
            
            if (success) {
                result.put("success", true);
                result.put("message", "额度更新成功");
                result.put("keyHash", keyHash);
                result.put("newLimit", limit);
            } else {
                result.put("success", false);
                result.put("message", "额度更新失败");
            }
        } catch (Exception e) {
            log.error("[RechargeController] 测试更新额度失败", e);
            result.put("success", false);
            result.put("message", "更新额度异常: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}
