package com.hsd.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.service.RechargeService;
import com.hsd.service.WechatPayService;
import com.hsd.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 充值功能控制器
 * 
 * 提供充值相关的 RESTful API 接口，包括：
 * - API Key 绑定与查询
 * - 创建充值订单
 * - 查询订单状态
 * - 充值历史记录
 * - 微信支付回调
 */
@Slf4j
@RestController
@RequestMapping("/api/recharge")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RechargeController {

    private final RechargeService rechargeService;
    private final WechatPayService wechatPayService;
    private final JwtUtil jwtUtil;

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
     * @param body 请求体，包含 apiKey 字段
     * @return 绑定结果响应
     */
    @PostMapping("/bind-key")
    public ResponseEntity<Map<String, Object>> bindKey(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        String apiKey = body.get("apiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "API Key 不能为空");
            return ResponseEntity.badRequest().body(result);
        }

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
     * 用户发起充值请求，创建微信 Native 支付订单，
     * 返回支付二维码链接供前端展示。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param body 请求体，包含 amountUsd 字段（充值金额，美元）
     * @return 订单创建结果，包含订单号和二维码链接
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body) {
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Object amountObj = body.get("amountUsd");
        if (amountObj == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "充值金额不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        BigDecimal amountUsd;
        try {
            amountUsd = new BigDecimal(amountObj.toString());
        } catch (NumberFormatException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "充值金额格式不正确");
            return ResponseEntity.badRequest().body(result);
        }

        Map<String, Object> result = rechargeService.createOrder(userId, amountUsd);
        result.put("mockMode", wechatPayService.isMockMode());
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
     * 微信支付回调接口
     * 
     * 接收微信支付服务器的支付结果通知，
     * 验证签名后处理支付成功逻辑（分配额度）。
     * 
     * 注意：此接口必须公网可访问，且支持 HTTPS。
     * 
     * @param request HTTP 请求对象
     * @return 处理结果响应（微信要求返回特定格式）
     */
    @PostMapping("/wechat/notify")
    public ResponseEntity<Map<String, String>> wechatNotify(HttpServletRequest request) {
        log.info("[RechargeController] 收到微信支付通知");
        
        try {
            String timestamp = request.getHeader("Wechatpay-Timestamp");
            String nonce = request.getHeader("Wechatpay-Nonce");
            String signature = request.getHeader("Wechatpay-Signature");
            
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();

            log.info("[RechargeController] 微信支付通知 body: {}", body);

            if (!wechatPayService.verifyNotify(timestamp, nonce, body, signature)) {
                log.error("[RechargeController] 微信支付通知验签失败");
                Map<String, String> result = new HashMap<>();
                result.put("code", "FAIL");
                result.put("message", "验签失败");
                return ResponseEntity.status(401).body(result);
            }

            JSONObject notifyData = wechatPayService.parseNotifyBody(body);
            if (notifyData == null) {
                log.error("[RechargeController] 解析通知数据失败");
                Map<String, String> result = new HashMap<>();
                result.put("code", "FAIL");
                result.put("message", "解析失败");
                return ResponseEntity.badRequest().body(result);
            }

            String eventType = notifyData.getString("event_type");
            if (!"TRANSACTION.SUCCESS".equals(eventType)) {
                log.info("[RechargeController] 非成功事件: {}", eventType);
                return ResponseEntity.ok(wechatPayService.buildSuccessResponse());
            }

            JSONObject resource = notifyData.getJSONObject("resource");
            String orderNo = resource.getString("out_trade_no");
            String wechatOrderId = resource.getString("transaction_id");
            String paidAt = resource.getString("success_time");

            log.info("[RechargeController] 支付成功: orderNo={}, wechatOrderId={}", orderNo, wechatOrderId);
            
            rechargeService.handlePaySuccess(orderNo, wechatOrderId, paidAt);

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
     * 模拟支付成功接口
     * 
     * 仅在模拟模式下可用，用于开发测试阶段手动触发支付成功流程。
     * 生产环境调用此接口将返回 403 错误。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param orderNo 商户订单号
     * @return 处理结果响应
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
}
