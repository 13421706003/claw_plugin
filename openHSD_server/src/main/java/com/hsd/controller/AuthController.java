package com.hsd.controller;

import com.hsd.dto.LoginRequest;
import com.hsd.dto.RegisterRequest;
import com.hsd.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 
 * 提供用户认证相关的 RESTful API，包括：
 * - 用户登录
 * - 用户注册
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final UserService userService;

    /**
     * 用户登录
     * 
     * 验证用户名和密码，成功后返回 JWT Token。
     * 
     * @param request 登录请求参数（username, password）
     * @return 登录结果，包含 token 和用户信息
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> data = userService.login(request.getUsername(), request.getPassword());
            result.put("success", true);
            result.put("message", "登录成功");
            result.putAll(data);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 用户注册
     * 
     * 创建新用户账号，密码长度至少6位。
     * 
     * @param request 注册请求参数（username, password）
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            userService.register(request.getUsername(), request.getPassword());
            result.put("success", true);
            result.put("message", "注册成功");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("[AuthController] 注册失败：", e);
            result.put("success", false);
            result.put("message", e.getMessage() != null ? e.getMessage() : "注册失败，请稍后重试");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AuthController] 注册异常：", e);
            result.put("success", false);
            result.put("message", "系统错误，请稍后重试");
            return ResponseEntity.ok(result);
        }
    }
}
