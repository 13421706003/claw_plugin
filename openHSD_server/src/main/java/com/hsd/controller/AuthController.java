package com.hsd.controller;

import com.hsd.dto.LoginRequest;
import com.hsd.dto.RegisterRequest;
import com.hsd.dto.UpdatePasswordRequest;
import com.hsd.dto.UpdateUsernameRequest;
import com.hsd.service.UserService;
import com.hsd.util.JwtUtil;
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
 * - 修改用户名
 * - 修改密码
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

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

    /**
     * 修改用户名
     * 
     * 用户修改自己的登录用户名，需验证新用户名是否已被占用。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param request 修改用户名请求参数（newUsername）
     * @return 修改结果
     */
    @PutMapping("/username")
    public ResponseEntity<Map<String, Object>> updateUsername(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody UpdateUsernameRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        try {
            userService.updateUsername(userId, request.getNewUsername());
            result.put("success", true);
            result.put("message", "用户名修改成功");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("[AuthController] 修改用户名失败：", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 修改密码
     * 
     * 用户修改登录密码，需验证旧密码是否正确。
     * 
     * @param authHeader Authorization 请求头（Bearer Token）
     * @param request 修改密码请求参数（oldPassword, newPassword）
     * @return 修改结果
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> updatePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody UpdatePasswordRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        Long userId = extractUserId(authHeader);
        if (userId == null) {
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        try {
            userService.updatePassword(userId, request.getOldPassword(), request.getNewPassword());
            result.put("success", true);
            result.put("message", "密码修改成功，请重新登录");
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("[AuthController] 修改密码失败：", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
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
            log.error("[AuthController] 解析token失败", e);
            return null;
        }
    }
}
